(function () {
    'use strict';
    const $root = $('#tcPage');
    if (!$root.length) { return; }
    const projectId = $root.data('project-id');
    const tier = String($root.data('tier') || '');
    const isTeam = tier === 'team';

    const PRIORITY = { High: { c: 'lvl-high', t: 'High' }, Medium: { c: 'lvl-med', t: 'Medium' }, Low: { c: 'lvl-low', t: 'Low' } };
    const STATUS = { passed: { cls: 'ok', t: '통과' }, failed: { cls: 'hot', t: '실패' }, pending: { cls: 'muted', t: '대기' } };

    function esc(s) { return $('<div>').text(s == null ? '' : s).html(); }
    function errMessage(xhr, f) { const r = xhr.responseJSON; return (r && r.message) ? r.message : (f || `오류 (HTTP ${xhr.status})`); }

    function priorityHtml(p) { const x = PRIORITY[p] || PRIORITY.Medium; return `<span class="lvl ${x.c}">${esc(x.t)}</span>`; }
    function statusHtml(s) { const x = STATUS[s] || STATUS.pending; return `<span class="pill ${x.cls}">${esc(x.t)}</span>`; }

    function load() {
        $.getJSON(`/api/projects/${projectId}/test-cases`).done(function (res) {
            const tcs = (res && res.data) || [];
            $('#tcCount').text(`${tcs.length}건`);
            const $list = $('#tcList');
            if (!tcs.length) { $list.html('<p class="page-sub" style="margin:0">테스트 케이스가 없습니다.</p>'); return; }
            $list.empty();
            tcs.forEach(function (tc) {
                const $row = $(`<div class="item" data-id="${esc(tc.id)}">
                    <span class="body"><b style="font-family:var(--mono)">${esc(tc.code)}</b> ${esc(tc.title)}
                        <span class="cat-tag" style="margin-left:6px">${esc(tc.phase)}</span></span>
                    <span style="display:flex;gap:12px;align-items:center">${priorityHtml(tc.priority)}${statusHtml(tc.status)}</span>
                </div>`);
                $row.on('click', function () { openDetail(tc.id); });
                $list.append($row);
            });
        });
    }

    function openDetail(tcId) {
        const $d = $('#tcDetail').prop('hidden', false).html('<p class="page-sub">불러오는 중…</p>');
        $.getJSON(`/api/projects/${projectId}/test-cases/${tcId}`).done(function (res) {
            const tc = res.data.testCase;
            const defects = res.data.linkedDefects || [];
            const statusSel = isTeam ? `
                <label class="page-sub">상태 변경
                    <select id="tcStatusSel" style="margin-left:6px;border:1px solid var(--divider);border-radius:6px;padding:4px 8px">
                        <option value="pending"${tc.status === 'pending' ? ' selected' : ''}>대기</option>
                        <option value="passed"${tc.status === 'passed' ? ' selected' : ''}>통과</option>
                        <option value="failed"${tc.status === 'failed' ? ' selected' : ''}>실패</option>
                    </select></label>` : '';
            const linked = defects.length
                ? defects.map(d => `<div class="item" style="cursor:default"><span class="body"><b style="font-family:var(--mono)">${esc(d.code)}</b> ${esc(d.title)}</span><span class="pill ${d.severity === 'High' ? 'hot' : 'muted'}">${esc(d.severity)}</span></div>`).join('')
                : '<p class="page-sub" style="margin:0">연결된 결함이 없습니다.</p>';
            const newDefectBtn = (tc.status === 'failed')
                ? `<a class="btn accent sm" href="/projects/${projectId}/defects?tc=${tc.id}">+ 결함 등록</a>` : '';
            $('#tcDetail').html(`
                <div class="card-head"><h3>${esc(tc.code)} · ${esc(tc.title)}</h3><span class="meta">${statusHtml(tc.status)}</span></div>
                <div class="form" style="gap:8px">
                    <div class="row"><label>단계</label><div class="value">${esc(tc.phase)} · ${priorityHtml(tc.priority)}</div></div>
                    <div class="row"><label>전제조건</label><div class="value" style="white-space:pre-wrap">${esc(tc.precondition || '—')}</div></div>
                    <div class="row"><label>절차</label><div class="value" style="white-space:pre-wrap">${esc(tc.steps || '—')}</div></div>
                    <div class="row"><label>기대결과</label><div class="value" style="white-space:pre-wrap">${esc(tc.expectedResult || '—')}</div></div>
                    <div class="row"><label>실제결과</label><div class="value" style="white-space:pre-wrap">${esc(tc.actualResult || '—')}</div></div>
                </div>
                <div style="margin-top:12px;display:flex;justify-content:space-between;align-items:center;gap:10px">${statusSel}<span id="tcDetailErr" class="err" style="display:none;color:var(--hot)"></span></div>
                <div class="card-head" style="margin-top:18px"><h3>연결 결함</h3>${newDefectBtn}</div>
                <div class="card-list">${linked}</div>`);
            $('#tcStatusSel').on('change', function () {
                const status = $(this).val();
                $.ajax({ url: `/api/projects/${projectId}/test-cases/${tcId}/status`, method: 'PATCH', contentType: 'application/json', data: JSON.stringify({ status: status }) })
                    .done(function () { openDetail(tcId); load(); })
                    .fail(function (xhr) { $('#tcDetailErr').text(errMessage(xhr, '변경 실패')).show(); });
            });
        }).fail(function (xhr) { $('#tcDetail').html(`<p class="err" style="color:var(--hot)">${esc(errMessage(xhr))}</p>`); });
    }

    function wire() {
        $('#newTcBtn').on('click', function () { $('#newTcCard').prop('hidden', false); $('#csvCard').prop('hidden', true); });
        $('#tcCancel').on('click', function () { $('#newTcCard').prop('hidden', true); });
        $('#csvBtn').on('click', function () { $('#csvCard').prop('hidden', false); $('#newTcCard').prop('hidden', true); });
        $('#csvCancel').on('click', function () { $('#csvCard').prop('hidden', true); });

        $('#tcForm').on('submit', function (e) {
            e.preventDefault();
            const $f = $(this); const $err = $('#tcErr').hide();
            const payload = {
                title: $f.find('[name=title]').val().trim(), phase: $f.find('[name=phase]').val().trim() || '기타',
                priority: $f.find('[name=priority]').val(), precondition: $f.find('[name=precondition]').val().trim() || null,
                steps: $f.find('[name=steps]').val().trim() || null, expectedResult: $f.find('[name=expectedResult]').val().trim() || null
            };
            $.ajax({ url: `/api/projects/${projectId}/test-cases`, method: 'POST', contentType: 'application/json', data: JSON.stringify(payload) })
                .done(function () { $('#newTcCard').prop('hidden', true); $f[0].reset(); load(); })
                .fail(function (xhr) { $err.text(errMessage(xhr, '등록 실패')).show(); });
        });

        $('#csvSubmit').on('click', function () {
            const csv = $('#csvText').val(); const $err = $('#csvErr').hide();
            if (!csv.trim()) { $err.text('CSV 내용을 입력해 주세요.').show(); return; }
            $.ajax({ url: `/api/projects/${projectId}/test-cases/import`, method: 'POST', contentType: 'application/json', data: JSON.stringify({ csv: csv }) })
                .done(function (res) { $('#csvCard').prop('hidden', true); $('#csvText').val(''); load(); })
                .fail(function (xhr) { $err.text(errMessage(xhr, '가져오기 실패')).show(); });
        });
    }

    $(function () { load(); wire(); });
})();
