(function () {
    'use strict';

    const $root = $('#slotPage');
    if (!$root.length) { return; }

    const projectId = $root.data('project-id');
    const slotType = String($root.data('slot'));
    const base = `/api/projects/${projectId}/slots/${slotType}`;

    function esc(s) { return $('<div>').text(s == null ? '' : s).html(); }

    function errMessage(xhr, fallback) {
        const res = xhr.responseJSON;
        return (res && res.message) ? res.message : (fallback || `오류 (HTTP ${xhr.status})`);
    }

    function showErr($box, xhr, fallback) {
        $box.text(errMessage(xhr, fallback)).show();
    }

    function reload() { window.location.reload(); }

    const SLOT_NAMES = { requirements: '요구사항 정의서', ia: 'IA', design: '디자인 시안', prototype: '프로토타입', figma: 'Figma' };
    const slotName = SLOT_NAMES[slotType] || slotType;

    function confirmModal(title, msg, okLabel, onOk, danger) {
        SPModal.open(`${SPModal.head(title)}
            <div class="modal-body"><div class="conf-msg">${msg}</div><div class="err" id="mErr" style="display:none;color:var(--hot);margin-top:8px"></div></div>
            <div class="modal-foot">
                <button class="btn" type="button" data-action="close-modal">취소</button>
                <button class="btn ${danger ? 'danger' : 'primary'}" type="button" id="mConfirmOk">${okLabel}</button>
            </div>`);
        $('#mConfirmOk').on('click', onOk);
    }

    function post(url, body) {
        return $.ajax({
            url: url, method: 'POST',
            contentType: 'application/json',
            data: body ? JSON.stringify(body) : undefined
        });
    }

    // --- 버전 드롭다운 토글 ---
    $root.on('click', '#versionBtn', function (e) {
        e.stopPropagation();
        $('#versionMenu').prop('hidden', function (i, v) { return !v; });
    });
    $(document).on('click', function (e) {
        if (!$(e.target).closest('#versionBtn').length && !$(e.target).closest('#versionMenu').length) {
            $('#versionMenu').prop('hidden', true);
        }
    });

    // --- 파일 업로드 ---
    $root.on('change', '#fileInput', function () {
        const file = this.files && this.files[0];
        if (!file) { return; }
        const $err = $('#fileErr').hide();
        const fd = new FormData();
        fd.append('file', file);
        $.ajax({ url: `${base}/files`, method: 'POST', data: fd, processData: false, contentType: false })
            .done(reload)
            .fail(function (xhr) { showErr($err, xhr, '업로드 실패'); });
    });

    function modalErr(xhr, fallback) {
        $('#mErr').text(errMessage(xhr, fallback)).show();
    }

    // --- 액션 위임 (프로토타입 모달) ---
    $root.on('click', '[data-action]', function () {
        const act = $(this).data('action');

        if (act === 'post-comment') {
            const $err2 = $('#commentErr').hide();
            const body = $('#newComment').val().trim();
            if (!body) { return; }
            post(`${base}/comments`, { body: body }).done(reload)
                .fail(function (xhr) { showErr($err2, xhr, '코멘트 등록 실패'); });
            return;
        }

        if (act === 'add-link') {
            SPModal.open(`${SPModal.head('외부 링크 추가')}
                <div class="modal-body">
                    <div class="row"><label>라벨</label><div class="value"><input type="text" id="mLinkLabel" placeholder="예: Figma 핸드오프"></div></div>
                    <div class="row"><label>URL</label><div class="value"><input type="url" id="mLinkUrl" placeholder="https://..."></div></div>
                    <div class="note">파일 없이 외부 링크(Figma 등)를 묶음에 추가합니다. 뷰어 대신 [열기]로 이동.</div>
                    <div class="err" id="mErr" style="display:none;color:var(--hot)"></div>
                </div>
                <div class="modal-foot"><button class="btn" type="button" data-action="close-modal">취소</button><button class="btn primary" type="button" id="mLinkOk">추가</button></div>`);
            $('#mLinkOk').on('click', function () {
                const label = $('#mLinkLabel').val().trim();
                const url = $('#mLinkUrl').val().trim();
                if (!url) { $('#mErr').text('URL을 입력하세요.').show(); return; }
                post(`${base}/urls`, { label: label, url: url }).done(reload).fail(function (x) { modalErr(x, '링크 추가 실패'); });
            });
        } else if (act === 'review-request') {
            SPModal.open(`${SPModal.head('검토 요청 발송')}
                <div class="modal-body">
                    <div class="row"><label>대상</label><div class="value static">${slotName}</div></div>
                    <div class="row"><label>요청 메시지<small class="opt">(선택)</small></label><div class="value"><textarea id="mReviewMsg" placeholder="고객사에 전달할 안내 메시지"></textarea></div></div>
                    <div class="note">발송 시 <b>현재 버전이 잠금</b>되고 고객사에 알림이 발송됩니다. 이후 변경하려면 새 버전 생성 필요.</div>
                    <div class="err" id="mErr" style="display:none;color:var(--hot)"></div>
                </div>
                <div class="modal-foot"><button class="btn" type="button" data-action="close-modal">취소</button><button class="btn primary" type="button" id="mReviewOk">요청 발송</button></div>`);
            $('#mReviewOk').on('click', function () {
                post(`${base}/review-request`).done(reload).fail(function (x) { modalErr(x, '검토 요청 실패'); });
            });
        } else if (act === 'reject') {
            SPModal.open(`${SPModal.head('검토 결과 — 반려')}
                <div class="modal-body">
                    <div class="row"><label>대상</label><div class="value static">${slotName}</div></div>
                    <div class="row"><label>사유</label><div class="value"><textarea id="mRejectReason" placeholder="반려 사유를 입력하세요."></textarea></div></div>
                    <div class="note">반려 사유는 워크플로우 활동 이력에 <b>영구 보존</b>됩니다.</div>
                    <div class="err" id="mErr" style="display:none;color:var(--hot)"></div>
                </div>
                <div class="modal-foot"><button class="btn" type="button" data-action="close-modal">취소</button><button class="btn danger" type="button" id="mRejectOk">반려 제출</button></div>`);
            $('#mRejectOk').on('click', function () {
                const reason = $('#mRejectReason').val().trim();
                if (!reason) { $('#mErr').text('반려 사유를 입력해 주세요.').show(); return; }
                post(`${base}/reject`, { reason: reason }).done(reload).fail(function (x) { modalErr(x, '반려 실패'); });
            });
        } else if (act === 'new-version') {
            SPModal.open(`${SPModal.head('새 버전 만들기 (draft 생성)')}
                <div class="modal-body">
                    <div class="row"><label>대상</label><div class="value static">${slotName}</div></div>
                    <div class="row"><label>변경 요약</label><div class="value"><textarea id="mChangeNote" placeholder="예: §5 보안 요구사항 추가, 오타 수정"></textarea></div></div>
                    <div class="note">생성 시 <b>새 스냅샷이 draft 상태로 생성</b>되고, 활동 이력에 생성 이벤트가 기록됩니다. 컨펌 상태에서 생성 시 자동 무효화.</div>
                    <div class="err" id="mErr" style="display:none;color:var(--hot)"></div>
                </div>
                <div class="modal-foot"><button class="btn" type="button" data-action="close-modal">취소</button><button class="btn primary" type="button" id="mNewVerOk">생성 (draft)</button></div>`);
            $('#mNewVerOk').on('click', function () {
                const summary = $('#mChangeNote').val().trim();
                if (!summary) { $('#mErr').text('변경 요약을 입력해 주세요.').show(); return; }
                post(`${base}/versions`, { changeSummary: summary }).done(reload).fail(function (x) { modalErr(x, '새 버전 생성 실패'); });
            });
        } else if (act === 'confirm') {
            confirmModal('컨펌', `<b>${slotName}</b> 현재 버전을 컨펌합니다. 컨펌 후에는 잠금되며, 다음 단계로 진행할 수 있습니다.`, '컨펌', function () {
                post(`${base}/confirm`).done(reload).fail(function (x) { modalErr(x, '컨펌 실패'); });
            });        } else if (act === 'review-recall') {
            confirmModal('검토 요청 회수', '검토 요청을 회수하면 다시 draft로 돌아가 편집할 수 있습니다.', '회수', function () {
                post(`${base}/review-recall`).done(reload).fail(function (x) { modalErr(x, '회수 실패'); });
            });        } else if (act === 'ack-upstream') {
            confirmModal('선행 검토 완료', '선행 산출물 변경이 이 산출물에 영향이 없음을 확인합니다.', '검토 완료', function () {
                post(`${base}/ack-upstream`).done(reload).fail(function (x) { modalErr(x, '처리 실패'); });
            });        }
    });

    // --- 파일/링크 삭제 (스타일드 confirm) ---
    $root.on('click', '[data-del]', function () {
        const id = $(this).data('del');
        const $file = $(this).closest('.file');
        const isUrl = $file.find('.badge').length > 0;
        const noun = isUrl ? '링크' : '파일';
        const name = $file.find('.name b').first().text() || noun;
        confirmModal(`${noun} 삭제`,
            `<b>${esc(name)}</b> ${noun}을(를) 정말 삭제하시겠습니까?<span class="conf-note">현재 draft 버전에서 제거됩니다. 이전 버전 스냅샷은 그대로 보존됩니다.</span>`,
            '삭제',
            function () {
                $.ajax({ url: `${base}/files/${id}`, method: 'DELETE' }).done(reload)
                    .fail(function (xhr) { modalErr(xhr, '삭제 실패'); });
            }, true);
    });

    // --- 파일 보기 ---
    $root.on('click', '[data-view]', function () {
        const id = $(this).data('view');
        const ct = String($(this).attr('data-ct') || '');
        const name = String($(this).attr('data-name') || '');
        const url = `${base}/files/${id}/download`;
        let inner;
        if (ct === 'application/pdf') {
            inner = `<iframe src="${url}" style="width:100%;height:72vh;border:0;border-radius:8px"></iframe>`;
        } else if (ct.indexOf('image/') === 0) {
            inner = `<img src="${url}" alt="${esc(name)}" style="max-width:100%;max-height:72vh;display:block;margin:0 auto">`;
        } else {
            inner = '<div style="padding:48px;text-align:center;color:var(--muted)">미리보기 미지원 형식 · 다운로드 후 확인하세요.</div>';
        }
        const $ov = $(`<div style="position:fixed;inset:0;background:rgba(0,0,0,.5);display:flex;align-items:center;justify-content:center;z-index:120;padding:24px">
            <div style="background:var(--surface);border-radius:12px;max-width:920px;width:100%;max-height:90vh;overflow:auto;box-shadow:var(--panel-shadow-hover)">
                <div style="display:flex;justify-content:space-between;align-items:center;padding:14px 18px;border-bottom:1px solid var(--divider)">
                    <b>${esc(name)}</b><button class="icon-action" data-close type="button" title="닫기">✕</button>
                </div>
                <div style="padding:16px">${inner}</div>
            </div></div>`);
        $ov.on('click', function (e) {
            if (e.target === $ov[0] || $(e.target).closest('[data-close]').length) { $ov.remove(); }
        });
        $('body').append($ov);
    });

    // --- 코멘트 삭제 (스타일드 confirm) ---
    $root.on('click', '[data-cdel]', function () {
        const id = $(this).data('cdel');
        const isAdmin = String($root.data('tier')) === 'admin';
        confirmModal('코멘트 삭제',
            `이 코멘트를 삭제하시겠습니까?${isAdmin ? '<span class="conf-note">관리자 강제 삭제 — 감사 로그에 기록됩니다.</span>' : ''}`,
            '삭제',
            function () {
                $.ajax({ url: `/api/projects/${projectId}/comments/${id}`, method: 'DELETE' }).done(reload)
                    .fail(function (xhr) { modalErr(xhr, '코멘트 삭제 실패'); });
            }, true);
    });

    // --- 코멘트 수정 ---
    $root.on('click', '[data-cedit]', function () {
        const id = $(this).data('cedit');
        const $item = $(this).closest('[data-cid]');
        const $body = $item.find('.comment-body');
        if ($item.find('textarea').length) { return; }
        const current = $body.text();
        const $editor = $(`<div class="comment-edit" style="margin-top:6px">
            <textarea class="comment-edit-ta"></textarea>
            <div class="comment-edit-actions" style="margin-top:6px;display:flex;gap:6px;justify-content:flex-end">
                <button class="btn sm" type="button" data-cedit-cancel>취소</button>
                <button class="btn primary sm" type="button" data-cedit-save="${esc(id)}">저장</button>
            </div></div>`);
        $editor.find('textarea').val(current);
        $body.hide().after($editor);
    });

    $root.on('click', '[data-cedit-cancel]', function () {
        const $editor = $(this).closest('.comment-edit');
        $editor.prev('.comment-body').show();
        $editor.remove();
    });

    $root.on('click', '[data-cedit-save]', function () {
        const id = $(this).data('cedit-save');
        const body = $(this).closest('.comment-edit').find('textarea').val().trim();
        const $err = $('#commentErr').hide();
        if (!body) { return; }
        $.ajax({
            url: `/api/projects/${projectId}/comments/${id}`, method: 'PATCH',
            contentType: 'application/json', data: JSON.stringify({ body: body })
        }).done(reload).fail(function (xhr) { showErr($err, xhr, '코멘트 수정 실패'); });
    });
})();
