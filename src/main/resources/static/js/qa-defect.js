(function () {
    'use strict';

    function errMessage(xhr, fallback) {
        const res = xhr.responseJSON;
        return (res && res.message) ? res.message : (fallback || `오류 (HTTP ${xhr.status})`);
    }
    function reload() { window.location.reload(); }
    function esc(s) { return $('<div>').text(s == null ? '' : s).html(); }

    const JOB_LABEL = { pm: 'PM', planner: '기획', designer: '디자인', developer: '개발', qa: 'QA' };
    function loadAssignees(pid, sel, currentId) {
        $.getJSON(`/api/projects/${pid}/members`).done(function (res) {
            ((res && res.data) || []).filter(m => m.accountTier === 'team').forEach(function (m) {
                const job = JOB_LABEL[m.accountJob] || m.accountJob;
                $(sel).append(`<option value="${m.accountId}">${esc(m.accountName)}${job ? ' (' + esc(job) + ')' : ''}</option>`);
            });
            if (currentId != null) { $(sel).val(String(currentId)); }
        });
    }

    // --- 목록 페이지 ---
    const $list = $('#defectPage');
    if ($list.length) {
        const pid = $list.data('project-id');
        const tier = $list.data('tier');

        $list.on('click', 'tr[data-href]', function () { window.location = $(this).data('href'); });

        $('#newDefectBtn').on('click', function () {
            SPModal.open(
                `${SPModal.head('결함 등록')}
                <div class="modal-body">
                    <div class="row"><label>제목</label><div class="value"><input type="text" id="ndTitle" maxlength="200" placeholder="예: 로그인 후 리다이렉트 미동작"></div></div>
                    <div class="row"><label>심각도</label><div class="value"><select id="ndSeverity"><option>Medium</option><option>High</option><option>Low</option></select></div></div>
                    <div class="row"><label>환경 <small class="opt">선택</small></label><div class="value"><input type="text" id="ndEnv" maxlength="200" placeholder="브라우저·OS·시각"></div></div>
                    <div class="row"><label>재현 단계 <small class="opt">선택</small></label><div class="value"><textarea id="ndRepro"></textarea></div></div>
                    <div class="row"><label>연결 TC <small class="opt">선택</small></label><div class="value"><select id="ndLinkTc"><option value="">연결 안 함</option></select></div></div>
                    ${tier === 'team' ? '<div class="row"><label>담당자 <small class="opt">선택</small></label><div class="value"><select id="ndAssignee"><option value="">미지정</option></select></div></div>' : ''}
                    <div class="note">등록 시 상태는 '미해결'로 시작합니다. 연결 TC는 발견 출처를 추적하며 생략 가능합니다.</div>
                    <p id="ndErr" class="err" style="display:none;color:var(--hot);margin-top:8px"></p>
                </div>
                <div class="modal-foot">
                    <button class="btn" type="button" data-action="close-modal">취소</button>
                    <button class="btn primary" type="button" id="ndSubmit">등록</button>
                </div>`);

            $.getJSON(`/api/projects/${pid}/test-cases`).done(function (res) {
                const tcs = (res && res.data) || [];
                const $sel = $('#ndLinkTc');
                tcs.forEach(t => $sel.append(`<option value="${t.id}">${esc(t.code)} · ${esc(t.title)}</option>`));
            });
            if (tier === 'team') { loadAssignees(pid, '#ndAssignee', null); }

            $('#ndSubmit').on('click', function () {
                const $err = $('#ndErr').hide();
                const tcVal = $('#ndLinkTc').val();
                const body = {
                    title: $('#ndTitle').val().trim(),
                    severity: $('#ndSeverity').val(),
                    environment: $('#ndEnv').val().trim() || null,
                    reproSteps: $('#ndRepro').val().trim() || null,
                    testCaseId: tcVal ? Number(tcVal) : null,
                    assigneeId: $('#ndAssignee').val() ? Number($('#ndAssignee').val()) : null
                };
                if (!body.title) { $err.text('제목을 입력하세요.').show(); return; }
                const $btn = $(this).prop('disabled', true);
                $.ajax({ url: `/api/projects/${pid}/defects`, method: 'POST', contentType: 'application/json', data: JSON.stringify(body) })
                    .done(reload).fail(function (xhr) { $err.text(errMessage(xhr, '등록 실패')).show(); $btn.prop('disabled', false); });
            });
        });
    }

    // --- 상세 페이지 ---
    const $detail = $('#defectDetailPage');
    if ($detail.length) {
        const pid = $detail.data('project-id');
        const defectId = $detail.data('defect-id');
        const tier = $detail.data('tier');

        $detail.on('click', '[data-defect-status]', function () {
            const status = $(this).data('defect-status');
            const $err = $('#defectDetailErr').hide();
            $.ajax({
                url: `/api/projects/${pid}/defects/${defectId}/status`, method: 'PATCH',
                contentType: 'application/json', data: JSON.stringify({ status: status })
            }).done(reload).fail(function (xhr) { $err.text(errMessage(xhr, '상태 변경 실패')).show(); });
        });

        // 결함 수정 (팀 + 본인 등록 고객사)
        $('#defectEditBtn').on('click', function () {
            $.getJSON(`/api/projects/${pid}/defects/${defectId}`).done(function (res) {
                const d = (res && res.data && res.data.defect) || {};
                SPModal.open(
                    `${SPModal.head('결함 수정')}
                    <div class="modal-body">
                        <div class="row"><label>제목</label><div class="value"><input type="text" id="ndTitle" maxlength="200"></div></div>
                        <div class="row"><label>심각도</label><div class="value"><select id="ndSeverity"><option>Medium</option><option>High</option><option>Low</option></select></div></div>
                        <div class="row"><label>환경 <small class="opt">선택</small></label><div class="value"><input type="text" id="ndEnv" maxlength="200" placeholder="브라우저·OS·시각"></div></div>
                        <div class="row"><label>재현 단계 <small class="opt">선택</small></label><div class="value"><textarea id="ndRepro"></textarea></div></div>
                        ${tier === 'team' ? '<div class="row"><label>담당자 <small class="opt">선택</small></label><div class="value"><select id="ndAssignee"><option value="">미지정</option></select></div></div>' : ''}
                        <p id="ndErr" class="err" style="display:none;color:var(--hot);margin-top:8px"></p>
                    </div>
                    <div class="modal-foot">
                        <button class="btn" type="button" data-action="close-modal">취소</button>
                        <button class="btn primary" type="button" id="ndSubmit">저장</button>
                    </div>`);
                $('#ndTitle').val(d.title || '');
                $('#ndSeverity').val(d.severity || 'Medium');
                $('#ndEnv').val(d.environment || '');
                $('#ndRepro').val(d.reproSteps || '');
                if (tier === 'team') { loadAssignees(pid, '#ndAssignee', d.assigneeId); }
                $('#ndSubmit').on('click', function () {
                    const $err = $('#ndErr').hide();
                    const body = {
                        title: $('#ndTitle').val().trim(),
                        severity: $('#ndSeverity').val(),
                        environment: $('#ndEnv').val().trim() || null,
                        reproSteps: $('#ndRepro').val().trim() || null,
                        assigneeId: $('#ndAssignee').val() ? Number($('#ndAssignee').val()) : null
                    };
                    if (!body.title) { $err.text('제목을 입력하세요.').show(); return; }
                    const $btn = $(this).prop('disabled', true);
                    $.ajax({ url: `/api/projects/${pid}/defects/${defectId}`, method: 'PATCH', contentType: 'application/json', data: JSON.stringify(body) })
                        .done(reload).fail(function (xhr) { $err.text(errMessage(xhr, '수정 실패')).show(); $btn.prop('disabled', false); });
                });
            }).fail(function (xhr) { alert(errMessage(xhr, '결함을 불러오지 못했습니다.')); });
        });

        // 연결 TC 해제
        $detail.on('click', '[data-unlink-tc]', function () {
            const tcId = $(this).data('unlink-tc');
            const $err = $('#defLinkErr').hide();
            $.ajax({ url: `/api/projects/${pid}/defects/${defectId}/test-cases/${tcId}`, method: 'DELETE' })
                .done(reload).fail(function (xhr) { $err.text(errMessage(xhr, '연결 해제 실패')).show(); });
        });

        $detail.on('change', '#attachInput', function () {
            const file = this.files && this.files[0];
            if (!file) { return; }
            const $err = $('#attachErr').hide();
            const fd = new FormData();
            fd.append('file', file);
            $.ajax({ url: `/api/projects/${pid}/defects/${defectId}/attachments`, method: 'POST', data: fd, processData: false, contentType: false })
                .done(reload).fail(function (xhr) { $err.text(errMessage(xhr, '첨부 실패')).show(); });
        });
    }
})();
