(function () {
    'use strict';

    function errMessage(xhr, fallback) {
        const res = xhr.responseJSON;
        return (res && res.message) ? res.message : (fallback || `오류 (HTTP ${xhr.status})`);
    }
    function reload() { window.location.reload(); }
    function esc(s) { return $('<div>').text(s == null ? '' : s).html(); }

    // 백엔드(RFC4180)와 동일한 클라이언트 파서 — 미리보기/건수 계산용
    function parseCsv(text) {
        if (text == null) { return []; }
        text = String(text).replace(/\r\n/g, '\n').replace(/\r/g, '\n');
        if (text.charCodeAt(0) === 0xFEFF) { text = text.slice(1); }
        const all = [];
        let fields = [], cur = '', inQ = false;
        for (let i = 0; i < text.length; i++) {
            const c = text[i];
            if (inQ) {
                if (c === '"') {
                    if (text[i + 1] === '"') { cur += '"'; i++; } else { inQ = false; }
                } else { cur += c; }
            } else if (c === '"') {
                inQ = true;
            } else if (c === ',') {
                fields.push(cur); cur = '';
            } else if (c === '\n') {
                fields.push(cur); all.push(fields); fields = []; cur = '';
            } else {
                cur += c;
            }
        }
        if (cur.length > 0 || fields.length > 0) { fields.push(cur); all.push(fields); }
        let rows = all.filter(r => r.some(f => f != null && f.trim() !== ''));
        if (rows.length) {
            const c0 = (rows[0][0] || '').trim();
            if (c0.toLowerCase() === 'title' || c0 === '제목') { rows = rows.slice(1); }
        }
        return rows;
    }

    function normPriority(p) {
        const t = (p || '').trim();
        if (/^high$/i.test(t) || t === '높음') { return 'High'; }
        if (/^low$/i.test(t) || t === '낮음') { return 'Low'; }
        return 'Medium';
    }

    const JOB_LABEL = { pm: 'PM', planner: '기획', designer: '디자인', developer: '개발', qa: 'QA' };
    // 담당자 select(#sel)를 프로젝트 팀 멤버로 채우고, currentId가 있으면 선택.
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
    const $list = $('#tcPage');
    if ($list.length) {
        const pid = $list.data('project-id');
        let csvContent = null;

        $list.on('click', 'tr[data-href]', function (e) {
            if ($(e.target).closest('.chk-cell').length) { return; } // 체크박스 클릭은 이동 안 함
            window.location = $(this).data('href');
        });

        // --- 담당자 일괄 지정 (체크박스 다중 선택 + shift 범위) ---
        let lastRow = null;
        function selectedTcIds() {
            return $('.tc-chk:checked').map(function () { return Number($(this).data('tc-id')); }).get();
        }
        function refreshBulkBtn() {
            const n = selectedTcIds().length;
            $('#bulkAssignBtn').prop('disabled', n === 0).text(n ? `담당자 일괄 지정 (${n})` : '담당자 일괄 지정');
        }
        $('#tcChkAll').on('change', function () {
            $('.tc-chk').prop('checked', this.checked);
            refreshBulkBtn();
        });
        $list.on('click', '.tc-chk', function (e) {
            if (e.shiftKey && lastRow != null) {
                const cur = Number($(this).data('row'));
                const lo = Math.min(cur, lastRow), hi = Math.max(cur, lastRow);
                const on = this.checked;
                $('.tc-chk').each(function () {
                    const r = Number($(this).data('row'));
                    if (r >= lo && r <= hi) { $(this).prop('checked', on); }
                });
            }
            lastRow = Number($(this).data('row'));
            refreshBulkBtn();
        });
        $('#bulkAssignBtn').on('click', function () {
            const ids = selectedTcIds();
            if (!ids.length) { return; }
            SPModal.open(
                `${SPModal.head('담당자 일괄 지정')}
                <div class="modal-body">
                    <div class="note">선택한 <b>${ids.length}건</b>의 TC에 담당자를 지정합니다.</div>
                    <div class="row"><label>담당자</label><div class="value"><select id="baAssignee"><option value="">미지정</option></select></div></div>
                    <p id="baErr" class="err" style="display:none;color:var(--hot);margin-top:8px"></p>
                </div>
                <div class="modal-foot">
                    <button class="btn" type="button" data-action="close-modal">취소</button>
                    <button class="btn primary" type="button" id="baSubmit">지정</button>
                </div>`);
            loadAssignees(pid, '#baAssignee', null);
            $('#baSubmit').on('click', function () {
                const $err = $('#baErr').hide();
                const aid = $('#baAssignee').val() ? Number($('#baAssignee').val()) : null;
                const $btn = $(this).prop('disabled', true);
                $.ajax({ url: `/api/projects/${pid}/test-cases/assign`, method: 'POST', contentType: 'application/json',
                    data: JSON.stringify({ testCaseIds: ids, assigneeId: aid }) })
                    .done(reload).fail(function (xhr) { $err.text(errMessage(xhr, '지정 실패')).show(); $btn.prop('disabled', false); });
            });
        });

        $('#newTcBtn').on('click', function () {
            SPModal.open(
                `${SPModal.head('TC 등록')}
                <div class="modal-body">
                    <div class="row"><label>제목</label><div class="value"><input type="text" id="ntTitle" maxlength="200" placeholder="예: 로그인 성공 케이스"></div></div>
                    <div class="row"><label>단계</label><div class="value"><input type="text" id="ntPhase" maxlength="30" placeholder="인증·요구사항·워크플로우 등"></div></div>
                    <div class="row"><label>우선순위</label><div class="value"><select id="ntPriority"><option>Medium</option><option>High</option><option>Low</option></select></div></div>
                    <div class="row"><label>전제조건 <small class="opt">선택</small></label><div class="value"><textarea id="ntPre"></textarea></div></div>
                    <div class="row"><label>절차 <small class="opt">선택</small></label><div class="value"><textarea id="ntSteps"></textarea></div></div>
                    <div class="row"><label>기대결과 <small class="opt">선택</small></label><div class="value"><textarea id="ntExpected"></textarea></div></div>
                    <div class="row"><label>담당자 <small class="opt">선택</small></label><div class="value"><select id="ntAssignee"><option value="">미지정</option></select></div></div>
                    <div class="note">등록 시 상태는 '대기'로 시작합니다.</div>
                    <p id="ntErr" class="err" style="display:none;color:var(--hot);margin-top:8px"></p>
                </div>
                <div class="modal-foot">
                    <button class="btn" type="button" data-action="close-modal">취소</button>
                    <button class="btn primary" type="button" id="ntSubmit">등록</button>
                </div>`);
            loadAssignees(pid, '#ntAssignee', null);
            $('#ntSubmit').on('click', function () {
                const $err = $('#ntErr').hide();
                const body = {
                    title: $('#ntTitle').val().trim(),
                    phase: $('#ntPhase').val().trim() || null,
                    priority: $('#ntPriority').val(),
                    precondition: $('#ntPre').val().trim() || null,
                    steps: $('#ntSteps').val().trim() || null,
                    expectedResult: $('#ntExpected').val().trim() || null,
                    assigneeId: $('#ntAssignee').val() ? Number($('#ntAssignee').val()) : null
                };
                if (!body.title) { $err.text('제목을 입력하세요.').show(); return; }
                const $btn = $(this).prop('disabled', true);
                $.ajax({ url: `/api/projects/${pid}/test-cases`, method: 'POST', contentType: 'application/json', data: JSON.stringify(body) })
                    .done(reload).fail(function (xhr) { $err.text(errMessage(xhr, '등록 실패')).show(); $btn.prop('disabled', false); });
            });
        });

        function downloadTemplate() {
            const header = '제목,단계,우선순위,전제조건,절차,기대결과';
            const r1 = '로그인 성공,인증,High,등록된 계정 존재,"1. 로그인 화면 진입\n2. 이메일·비밀번호 입력\n3. 로그인 버튼 클릭","대시보드로 이동, 세션 토큰 발급"';
            const r2 = '"제목 누락 검증",요구사항,Medium,,"1. 제목을 비운다\n2. 저장을 시도한다","유효성 오류 표시, 저장 차단"';
            const blob = new Blob(['\uFEFF' + [header, r1, r2].join('\r\n') + '\r\n'], { type: 'text/csv;charset=utf-8' });
            const a = document.createElement('a');
            a.href = URL.createObjectURL(blob);
            a.download = 'tc-template.csv';
            document.body.appendChild(a); a.click(); a.remove();
            setTimeout(function () { URL.revokeObjectURL(a.href); }, 0);
        }

        function renderPreview(fileName, text) {
            const rows = parseCsv(text);
            const valid = rows.filter(r => (r[0] || '').trim() !== '');
            const errors = rows.length - valid.length;
            const head = `<div class="dz-result"><b>${esc(fileName)}</b> · ${rows.length}행 인식 · `
                + `<span style="color:${errors ? 'var(--hot)' : 'var(--ok)'}">오류 ${errors}</span></div>`;
            const body = valid.slice(0, 5).map(r =>
                `<tr><td>${esc((r[0] || '').trim())}</td><td>${esc((r[1] || '').trim()) || '-'}</td>`
                + `<td>${esc(normPriority(r[2]))}</td></tr>`).join('');
            const more = valid.length > 5
                ? `<tr><td colspan="3" style="text-align:center;color:var(--muted);font-size:12px">… 외 ${valid.length - 5}행</td></tr>`
                : '';
            $('#tcImpPreview').html(head
                + `<div class="card flat" style="padding:0;overflow:hidden;margin-top:8px"><table class="tbl">`
                + `<thead><tr><th>제목</th><th>단계</th><th>우선순위</th></tr></thead><tbody>${body}${more}</tbody></table></div>`)
                .show();
            $('#tcImpDrop').addClass('has-file');
            $('#csvSubmit').text(valid.length ? `${valid.length}건 가져오기` : '가져오기').prop('disabled', valid.length === 0);
            if (errors) {
                $('#csvErr').text(`제목이 비어 있는 ${errors}행이 있습니다. 모든 행에 제목이 있어야 가져올 수 있습니다.`).show();
            } else {
                $('#csvErr').hide();
            }
        }

        function handleFile(file) {
            $('#csvErr').hide();
            if (!file) { return; }
            if (file.size > 5 * 1024 * 1024) { $('#csvErr').text('파일이 5MB를 초과합니다.').show(); return; }
            const reader = new FileReader();
            reader.onload = function (e) { csvContent = e.target.result; renderPreview(file.name, csvContent); };
            reader.onerror = function () { $('#csvErr').text('파일을 읽을 수 없습니다.').show(); };
            reader.readAsText(file, 'UTF-8');
        }

        $('#csvBtn').on('click', function () {
            csvContent = null;
            SPModal.open(
                `${SPModal.head('TC 일괄 가져오기')}
                <div class="modal-body">
                    <div class="note">CSV 파일로 여러 TC를 한 번에 등록합니다. 컬럼: 제목 · 단계 · 우선순위(High/Medium/Low) · 전제조건 · 절차 · 기대결과. 첫 헤더 행은 자동 무시.</div>
                    <div style="margin-top:6px;text-align:right"><a id="csvTemplate" style="color:var(--accent);cursor:pointer;font-weight:600;font-size:13px">CSV 템플릿 다운로드</a></div>
                    <label class="dropzone" id="tcImpDrop">
                        <div class="dz-icon">⬆</div>
                        <div class="dz-text"><b>CSV 파일을 끌어놓거나 클릭하여 선택</b><br><span>최대 5MB · .csv</span></div>
                        <input type="file" id="csvFile" accept=".csv,text/csv" hidden>
                    </label>
                    <div id="tcImpPreview" style="display:none"></div>
                    <p id="csvErr" class="err" style="display:none;color:var(--hot);margin-top:8px"></p>
                </div>
                <div class="modal-foot">
                    <button class="btn" type="button" data-action="close-modal">취소</button>
                    <button class="btn primary" id="csvSubmit" type="button" disabled>가져오기</button>
                </div>`);

            $('#csvTemplate').on('click', downloadTemplate);
            $('#csvFile').on('change', function () { handleFile(this.files && this.files[0]); });

            const drop = document.getElementById('tcImpDrop');
            ['dragover', 'dragenter'].forEach(ev => drop.addEventListener(ev, function (e) {
                e.preventDefault(); drop.classList.add('drag-over');
            }));
            drop.addEventListener('dragleave', function (e) { e.preventDefault(); drop.classList.remove('drag-over'); });
            drop.addEventListener('drop', function (e) {
                e.preventDefault(); drop.classList.remove('drag-over');
                handleFile(e.dataTransfer && e.dataTransfer.files && e.dataTransfer.files[0]);
            });

            $('#csvSubmit').on('click', function () {
                const $err = $('#csvErr').hide();
                if (!csvContent || !csvContent.trim()) { $err.text('CSV 파일을 선택하세요.').show(); return; }
                const $btn = $(this).prop('disabled', true);
                $.ajax({ url: `/api/projects/${pid}/test-cases/import`, method: 'POST', contentType: 'application/json', data: JSON.stringify({ csv: csvContent }) })
                    .done(reload)
                    .fail(function (xhr) { $err.text(errMessage(xhr, '가져오기 실패')).show(); $btn.prop('disabled', false); });
            });
        });
    }

    // --- 상세 페이지 ---
    const $detail = $('#tcDetailPage');
    if ($detail.length) {
        const pid = $detail.data('project-id');
        const tcId = $detail.data('tc-id');
        const tcCode = (document.querySelector('.page-title')?.textContent || '').split('·')[0].trim();

        $detail.on('click', '[data-tc-result]', function () {
            const status = $(this).data('tc-result');
            const actualResult = $('#actualResult').val();
            const $err = $('#tcDetailErr').hide();
            $.ajax({
                url: `/api/projects/${pid}/test-cases/${tcId}/status`, method: 'PATCH',
                contentType: 'application/json', data: JSON.stringify({ status: status, actualResult: actualResult })
            }).done(reload).fail(function (xhr) { $err.text(errMessage(xhr, '상태 변경 실패')).show(); });
        });

        // TC 수정 (팀 전용)
        $('#tcEditBtn').on('click', function () {
            $.getJSON(`/api/projects/${pid}/test-cases/${tcId}`).done(function (res) {
                const t = (res && res.data && res.data.testCase) || {};
                SPModal.open(
                    `${SPModal.head('TC 수정')}
                    <div class="modal-body">
                        <div class="row"><label>제목</label><div class="value"><input type="text" id="ntTitle" maxlength="200"></div></div>
                        <div class="row"><label>단계</label><div class="value"><input type="text" id="ntPhase" maxlength="30" placeholder="인증·요구사항·워크플로우 등"></div></div>
                        <div class="row"><label>우선순위</label><div class="value"><select id="ntPriority"><option>Medium</option><option>High</option><option>Low</option></select></div></div>
                        <div class="row"><label>전제조건 <small class="opt">선택</small></label><div class="value"><textarea id="ntPre"></textarea></div></div>
                        <div class="row"><label>절차 <small class="opt">선택</small></label><div class="value"><textarea id="ntSteps"></textarea></div></div>
                        <div class="row"><label>기대결과 <small class="opt">선택</small></label><div class="value"><textarea id="ntExpected"></textarea></div></div>
                        <div class="row"><label>담당자 <small class="opt">선택</small></label><div class="value"><select id="ntAssignee"><option value="">미지정</option></select></div></div>
                        <p id="ntErr" class="err" style="display:none;color:var(--hot);margin-top:8px"></p>
                    </div>
                    <div class="modal-foot">
                        <button class="btn" type="button" data-action="close-modal">취소</button>
                        <button class="btn primary" type="button" id="ntSubmit">저장</button>
                    </div>`);
                $('#ntTitle').val(t.title || '');
                $('#ntPhase').val(t.phase || '');
                $('#ntPriority').val(t.priority || 'Medium');
                $('#ntPre').val(t.precondition || '');
                $('#ntSteps').val(t.steps || '');
                $('#ntExpected').val(t.expectedResult || '');
                loadAssignees(pid, '#ntAssignee', t.assigneeId);
                $('#ntSubmit').on('click', function () {
                    const $err = $('#ntErr').hide();
                    const body = {
                        title: $('#ntTitle').val().trim(),
                        phase: $('#ntPhase').val().trim() || null,
                        priority: $('#ntPriority').val(),
                        precondition: $('#ntPre').val().trim() || null,
                        steps: $('#ntSteps').val().trim() || null,
                        expectedResult: $('#ntExpected').val().trim() || null,
                        assigneeId: $('#ntAssignee').val() ? Number($('#ntAssignee').val()) : null
                    };
                    if (!body.title) { $err.text('제목을 입력하세요.').show(); return; }
                    if (!body.phase) { $err.text('단계를 입력하세요.').show(); return; }
                    const $btn = $(this).prop('disabled', true);
                    $.ajax({ url: `/api/projects/${pid}/test-cases/${tcId}`, method: 'PATCH', contentType: 'application/json', data: JSON.stringify(body) })
                        .done(reload).fail(function (xhr) { $err.text(errMessage(xhr, '수정 실패')).show(); $btn.prop('disabled', false); });
                });
            }).fail(function (xhr) { alert(errMessage(xhr, 'TC를 불러오지 못했습니다.')); });
        });

        // 연결 결함 해제
        $detail.on('click', '[data-unlink-defect]', function () {
            const defectId = $(this).data('unlink-defect');
            const $err = $('#tcLinkErr').hide();
            $.ajax({ url: `/api/projects/${pid}/defects/${defectId}/test-cases/${tcId}`, method: 'DELETE' })
                .done(reload).fail(function (xhr) { $err.text(errMessage(xhr, '연결 해제 실패')).show(); });
        });

        // + 결함 등록 (이 TC에 자동 연결)
        $('#newDefectFromTc').on('click', function () {
            SPModal.open(
                `${SPModal.head('결함 등록')}
                <div class="modal-body">
                    <div class="row"><label>제목</label><div class="value"><input type="text" id="ndTitle" maxlength="200" placeholder="예: 로그인 후 리다이렉트 미동작"></div></div>
                    <div class="row"><label>심각도</label><div class="value"><select id="ndSeverity"><option>Medium</option><option>High</option><option>Low</option></select></div></div>
                    <div class="row"><label>환경 <small class="opt">선택</small></label><div class="value"><input type="text" id="ndEnv" maxlength="200" placeholder="브라우저·OS·시각"></div></div>
                    <div class="row"><label>재현 단계 <small class="opt">선택</small></label><div class="value"><textarea id="ndRepro"></textarea></div></div>
                    <div class="note">등록 시 상태는 '미해결'로 시작하며, <b>${esc(tcCode)}</b>에 자동 연결됩니다.</div>
                    <p id="ndErr" class="err" style="display:none;color:var(--hot);margin-top:8px"></p>
                </div>
                <div class="modal-foot">
                    <button class="btn" type="button" data-action="close-modal">취소</button>
                    <button class="btn primary" type="button" id="ndSubmit">등록</button>
                </div>`);
            $('#ndSubmit').on('click', function () {
                const $err = $('#ndErr').hide();
                const body = {
                    title: $('#ndTitle').val().trim(),
                    severity: $('#ndSeverity').val(),
                    environment: $('#ndEnv').val().trim() || null,
                    reproSteps: $('#ndRepro').val().trim() || null,
                    testCaseId: Number(tcId)
                };
                if (!body.title) { $err.text('제목을 입력하세요.').show(); return; }
                const $btn = $(this).prop('disabled', true);
                $.ajax({ url: `/api/projects/${pid}/defects`, method: 'POST', contentType: 'application/json', data: JSON.stringify(body) })
                    .done(reload).fail(function (xhr) { $err.text(errMessage(xhr, '등록 실패')).show(); $btn.prop('disabled', false); });
            });
        });

        // + 결함 연결 (기존 결함 연결)
        $('#linkDefectBtn').on('click', function () {
            const linkedIds = [...document.querySelectorAll('#linkedDefectList [data-defect-id]')]
                .map(el => Number(el.getAttribute('data-defect-id')));
            $.getJSON(`/api/projects/${pid}/defects`).done(function (res) {
                const all = (res && res.data) || [];
                const candidates = all.filter(d => linkedIds.indexOf(d.id) === -1);
                const STATUS = { resolved: '해결 완료', in_progress: '진행 중', cannot_reproduce: '재현 불가', open: '미해결' };
                const rows = candidates.length
                    ? candidates.map(d => `<label style="display:flex;align-items:center;gap:10px;padding:9px 2px;border-bottom:1px solid var(--divider);cursor:pointer">
                        <input type="checkbox" value="${d.id}">
                        <span style="flex:1;font-size:13px"><b>${esc(d.code)}</b> ${esc(d.title)}</span>
                        <span class="pill muted" style="font-size:11px">${esc(STATUS[d.status] || d.status)}</span></label>`).join('')
                    : '<div class="page-sub" style="padding:10px 0;margin:0">연결할 수 있는 결함이 없습니다.</div>';
                SPModal.open(
                    `${SPModal.head('기존 결함 연결')}
                    <div class="modal-body">
                        <div class="note">이 TC(<b>${esc(tcCode)}</b>)에 연결할 기존 결함을 선택하세요. 결함↔TC는 다대다로 연결됩니다.</div>
                        <div style="margin-top:8px">${rows}</div>
                        <p id="ldErr" class="err" style="display:none;color:var(--hot);margin-top:8px"></p>
                    </div>
                    <div class="modal-foot">
                        <button class="btn" type="button" data-action="close-modal">취소</button>
                        <button class="btn primary" type="button" id="ldSubmit" ${candidates.length ? '' : 'disabled'}>연결</button>
                    </div>`);
                $('#ldSubmit').on('click', function () {
                    const ids = [...document.querySelectorAll('#modalRoot input[type=checkbox]:checked')].map(c => c.value);
                    const $err = $('#ldErr').hide();
                    if (!ids.length) { $err.text('연결할 결함을 선택하세요.').show(); return; }
                    const $btn = $(this).prop('disabled', true);
                    $.when.apply($, ids.map(id => $.ajax({
                        url: `/api/projects/${pid}/defects/${id}/test-cases/${tcId}`, method: 'POST'
                    }))).done(reload).fail(function (xhr) { $err.text(errMessage(xhr, '연결 실패')).show(); $btn.prop('disabled', false); });
                });
            }).fail(function (xhr) { alert(errMessage(xhr, '결함 목록을 불러오지 못했습니다.')); });
        });
    }
})();
