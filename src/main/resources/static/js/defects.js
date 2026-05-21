(function () {
    'use strict';
    const $root = $('#defectPage');
    if (!$root.length) { return; }
    const projectId = $root.data('project-id');
    const tier = String($root.data('tier') || '');
    const isTeam = tier === 'team';

    const SEV = { High: 'lvl-high', Medium: 'lvl-med', Low: 'lvl-low' };
    const STATUS = {
        open: { cls: 'hot', t: '미해결' }, in_progress: { cls: 'warn', t: '진행중' },
        resolved: { cls: 'ok', t: '해결완료' }, cannot_reproduce: { cls: 'muted', t: '재현불가' }
    };

    function esc(s) { return $('<div>').text(s == null ? '' : s).html(); }
    function errMessage(xhr, f) { const r = xhr.responseJSON; return (r && r.message) ? r.message : (f || `오류 (HTTP ${xhr.status})`); }
    function sevHtml(s) { return `<span class="lvl ${SEV[s] || 'lvl-med'}">${esc(s)}</span>`; }
    function statusHtml(s) { const x = STATUS[s] || { cls: 'muted', t: s }; return `<span class="pill ${x.cls}">${esc(x.t)}</span>`; }
    function fmtSize(b) { if (b == null) return ''; if (b < 1024) return b + ' B'; if (b < 1048576) return (b / 1024).toFixed(1) + ' KB'; return (b / 1048576).toFixed(1) + ' MB'; }
    function qparam(k) { return new URLSearchParams(location.search).get(k); }

    function load() {
        $.getJSON(`/api/projects/${projectId}/defects`).done(function (res) {
            const defects = (res && res.data) || [];
            $('#defectCount').text(`${defects.length}건`);
            const $list = $('#defectList');
            if (!defects.length) { $list.html('<p class="page-sub" style="margin:0">결함이 없습니다.</p>'); return; }
            $list.empty();
            defects.forEach(function (d) {
                const $row = $(`<div class="item" data-id="${esc(d.id)}">
                    <span class="body"><b style="font-family:var(--mono)">${esc(d.code)}</b> ${esc(d.title)}</span>
                    <span style="display:flex;gap:12px;align-items:center">${sevHtml(d.severity)}${statusHtml(d.status)}</span>
                </div>`);
                $row.on('click', function () { openDetail(d.id); });
                $list.append($row);
            });
        });
    }

    function openDetail(defectId) {
        const $d = $('#defectDetail').prop('hidden', false).html('<p class="page-sub">불러오는 중…</p>');
        $.getJSON(`/api/projects/${projectId}/defects/${defectId}`).done(function (res) {
            const d = res.data.defect;
            const tcs = res.data.linkedTestCases || [];
            const atts = res.data.attachments || [];
            const statusSel = isTeam ? `
                <label class="page-sub">상태
                    <select id="defStatusSel" style="margin-left:6px;border:1px solid var(--divider);border-radius:6px;padding:4px 8px">
                        ${Object.keys(STATUS).map(k => `<option value="${k}"${d.status === k ? ' selected' : ''}>${STATUS[k].t}</option>`).join('')}
                    </select></label>` : '';
            const tcRows = tcs.length
                ? tcs.map(t => `<div class="item" style="cursor:default"><span class="body"><b style="font-family:var(--mono)">${esc(t.code)}</b> ${esc(t.title)}</span></div>`).join('')
                : '<p class="page-sub" style="margin:0">연결된 TC가 없습니다.</p>';
            const attRows = atts.length
                ? atts.map(a => attRow(defectId, a)).join('')
                : '<p class="page-sub" style="margin:0">첨부가 없습니다.</p>';
            $('#defectDetail').html(`
                <div class="card-head"><h3>${esc(d.code)} · ${esc(d.title)}</h3><span class="meta">${sevHtml(d.severity)} ${statusHtml(d.status)}</span></div>
                <div class="form" style="gap:8px">
                    <div class="row"><label>등록자</label><div class="value">${esc(d.reporterName || '—')}</div></div>
                    <div class="row"><label>환경</label><div class="value">${esc(d.environment || '—')}</div></div>
                    <div class="row"><label>재현 단계</label><div class="value" style="white-space:pre-wrap">${esc(d.reproSteps || '—')}</div></div>
                </div>
                <div style="margin-top:12px;display:flex;justify-content:space-between;align-items:center;gap:10px">${statusSel}<span id="defDetailErr" class="err" style="display:none;color:var(--hot)"></span></div>
                <div class="card-head" style="margin-top:18px"><h3>연결 TC</h3></div>
                <div class="card-list">${tcRows}</div>
                <div class="card-head" style="margin-top:18px"><h3>첨부</h3></div>
                <div class="card-list" id="attList">${attRows}</div>
                <div style="margin-top:10px"><label class="btn accent sm" style="cursor:pointer">첨부 업로드<input type="file" id="attInput" hidden></label></div>`);
            wireDetail(defectId);
        }).fail(function (xhr) { $('#defectDetail').html(`<p class="err" style="color:var(--hot)">${esc(errMessage(xhr))}</p>`); });
    }

    function attRow(defectId, a) {
        const url = `/api/projects/${projectId}/defects/${defectId}/attachments/${a.id}/download`;
        return `<div class="item" style="cursor:default"><span class="body"><b>${esc(a.originalName)}</b></span>
            <span style="display:flex;gap:8px;align-items:center"><span class="t">${esc(fmtSize(a.sizeBytes))}</span>
            <a class="btn sm" href="${url}" download="${esc(a.originalName)}">다운로드</a>
            <button class="icon-action danger" data-attdel="${esc(a.id)}" title="삭제">✕</button></span></div>`;
    }

    function wireDetail(defectId) {
        $('#defStatusSel').on('change', function () {
            const status = $(this).val();
            $.ajax({ url: `/api/projects/${projectId}/defects/${defectId}/status`, method: 'PATCH', contentType: 'application/json', data: JSON.stringify({ status: status }) })
                .done(function () { openDetail(defectId); load(); })
                .fail(function (xhr) { $('#defDetailErr').text(errMessage(xhr, '변경 실패')).show(); });
        });
        $('#attInput').on('change', function () {
            const file = this.files && this.files[0]; if (!file) return;
            const fd = new FormData(); fd.append('file', file);
            $.ajax({ url: `/api/projects/${projectId}/defects/${defectId}/attachments`, method: 'POST', data: fd, processData: false, contentType: false })
                .done(function () { openDetail(defectId); })
                .fail(function (xhr) { $('#defDetailErr').text(errMessage(xhr, '업로드 실패')).show(); });
        });
        $('#attList').on('click', '[data-attdel]', function () {
            const id = $(this).data('attdel');
            $.ajax({ url: `/api/projects/${projectId}/defects/${defectId}/attachments/${id}`, method: 'DELETE' })
                .done(function () { openDetail(defectId); })
                .fail(function (xhr) { $('#defDetailErr').text(errMessage(xhr, '삭제 실패')).show(); });
        });
    }

    function loadTcOptions(preselect) {
        $.getJSON(`/api/projects/${projectId}/test-cases`).done(function (res) {
            const tcs = (res && res.data) || [];
            const $sel = $('#defectForm [name=testCaseId]');
            tcs.forEach(function (t) { $sel.append(`<option value="${t.id}">${esc(t.code)} · ${esc(t.title)}</option>`); });
            if (preselect) { $sel.val(String(preselect)); }
        });
    }

    function wire() {
        $('#newDefectBtn').on('click', function () { openForm(null); });
        $('#defectCancel').on('click', function () { $('#newDefectCard').prop('hidden', true); });
        $('#defectForm').on('submit', function (e) {
            e.preventDefault();
            const $f = $(this); const $err = $('#defectErr').hide();
            const payload = {
                title: $f.find('[name=title]').val().trim(), severity: $f.find('[name=severity]').val(),
                environment: $f.find('[name=environment]').val().trim() || null,
                reproSteps: $f.find('[name=reproSteps]').val().trim() || null,
                testCaseId: $f.find('[name=testCaseId]').val() ? Number($f.find('[name=testCaseId]').val()) : null
            };
            $.ajax({ url: `/api/projects/${projectId}/defects`, method: 'POST', contentType: 'application/json', data: JSON.stringify(payload) })
                .done(function () { $('#newDefectCard').prop('hidden', true); $f[0].reset(); load(); })
                .fail(function (xhr) { $err.text(errMessage(xhr, '등록 실패')).show(); });
        });
    }

    function openForm(preselectTc) {
        $('#newDefectCard').prop('hidden', false);
        $('#defectForm')[0].reset();
        $('#defectForm [name=testCaseId]').find('option:gt(0)').remove();
        loadTcOptions(preselectTc);
    }

    $(function () {
        load(); wire();
        const tc = qparam('tc');
        if (tc) { openForm(tc); }
    });
})();
