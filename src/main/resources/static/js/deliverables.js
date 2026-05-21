(function () {
    'use strict';

    const $root = $('#deliverables');
    if (!$root.length) {
        return;
    }
    const projectId = $root.data('project-id');
    const canEdit = $root.data('can-edit') === true || $root.data('can-edit') === 'true';
    const tier = String($root.data('tier') || '');
    const myId = String($root.data('uid') || '');

    const SLOT_LABEL = {
        requirements: '요구사항', ia: 'IA', design: '디자인 시안', prototype: '프로토타입', figma: 'Figma'
    };
    const STATUS = {
        'empty': { label: '비어 있음', cls: 'muted' },
        'draft': { label: '작성 중', cls: 'accent' },
        'pending-review': { label: '검토 중', cls: 'warn' },
        'confirmed': { label: '컨펌', cls: 'ok' },
        'rejected': { label: '반려', cls: 'hot' }
    };
    const GATE_LABEL = { 9: '요구사항', 11: 'IA', 13: '디자인', 15: '프로토타입', 22: 'UAT' };
    const GATE_STATUS = {
        'pass': { txt: '통과', cls: 'ok' }, 'wait': { txt: '대기', cls: 'warn' }, 'lock': { txt: '잠김', cls: 'muted' }
    };
    const ACT_LABEL = {
        version_created: '버전 생성', review_requested: '검토 요청', review_recalled: '검토 회수',
        confirmed: '컨펌', rejected: '반려', invalidated: '무효화', upstream_reviewed: '선행 검토 완료'
    };

    function esc(s) { return $('<div>').text(s == null ? '' : s).html(); }

    function errMessage(xhr, fallback) {
        const res = xhr.responseJSON;
        return (res && res.message) ? res.message : (fallback || `오류 (HTTP ${xhr.status})`);
    }

    function fmtSize(bytes) {
        if (bytes == null) { return ''; }
        if (bytes < 1024) { return bytes + ' B'; }
        if (bytes < 1024 * 1024) { return (bytes / 1024).toFixed(1) + ' KB'; }
        return (bytes / 1024 / 1024).toFixed(1) + ' MB';
    }

    function fmtDate(iso) {
        if (!iso) { return ''; }
        const d = new Date(iso);
        if (isNaN(d)) { return ''; }
        const p = (n) => String(n).padStart(2, '0');
        return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`;
    }

    function statusPill(status) {
        const s = STATUS[status] || { label: status, cls: 'muted' };
        return `<span class="pill ${s.cls}">${esc(s.label)}</span>`;
    }

    function refresh(slotType) {
        if (slotType) { openSlot(slotType); }
        loadSlots();
        loadGates();
    }

    function loadGates() {
        $.getJSON(`/api/projects/${projectId}/gates`).done(function (res) {
            const gates = (res && res.data) || [];
            const $strip = $('#gateStrip').empty();
            $strip.append('<span class="label">게이트</span>');
            gates.forEach(function (g) {
                const s = GATE_STATUS[g.status] || { txt: g.status, cls: 'muted' };
                $strip.append(`<span class="pill ${s.cls}">G${g.gateStage} ${esc(GATE_LABEL[g.gateStage] || '')} · ${s.txt}</span>`);
            });
        });
    }

    function loadSlots() {
        $.getJSON(`/api/projects/${projectId}/slots`)
            .done(function (res) {
                const slots = (res && res.data) || [];
                const $grid = $('#slotGrid').empty();
                slots.forEach(function (slot) { $grid.append(renderSlotCard(slot)); });
            })
            .fail(function (xhr) {
                $('#slotGrid').html(`<p class="err" style="color:var(--hot)">${esc(errMessage(xhr, '슬롯 조회 실패'))}</p>`);
            });
    }

    function renderSlotCard(slot) {
        const ver = slot.currentVersionNo ? `v${slot.currentVersionNo}` : '—';
        const badge = slot.upstreamChanged
            ? '<span class="pill warn" style="margin-top:6px">선행 변경 · 검토 권장</span>' : '';
        const $card = $(`
            <div class="deliverable" data-slot="${esc(slot.slotType)}">
                <div style="display:flex;justify-content:space-between;width:100%;align-items:flex-start;gap:8px">
                    <b style="font-size:14px">${esc(SLOT_LABEL[slot.slotType] || slot.slotType)}</b>
                    ${statusPill(slot.status)}
                </div>
                <span style="margin-top:auto;font:500 11px/1 var(--mono);color:var(--muted)">${ver}</span>
                ${badge}
            </div>
        `);
        $card.on('click', function () { openSlot(slot.slotType); });
        return $card;
    }

    function openSlot(slotType) {
        const $detail = $('#slotDetail');
        $detail.prop('hidden', false).html('<p class="page-sub">불러오는 중…</p>');
        $.getJSON(`/api/projects/${projectId}/slots/${slotType}`)
            .done(function (res) { renderDetail(res.data); })
            .fail(function (xhr) {
                $detail.html(`<p class="err" style="color:var(--hot)">${esc(errMessage(xhr, '상세 조회 실패'))}</p>`);
            });
    }

    function actionButtons(slot) {
        const st = slot.status;
        const btns = [];
        if (st === 'draft' && tier === 'team') {
            btns.push('<button class="btn primary" data-act="review-request">검토 요청</button>');
        }
        if (st === 'pending-review' && tier === 'team') {
            btns.push('<button class="btn" data-act="review-recall">검토 요청 회수</button>');
        }
        if (st === 'pending-review' && tier === 'client') {
            btns.push('<button class="btn ok" data-act="confirm">컨펌</button>');
            btns.push('<button class="btn danger" data-act="reject">반려</button>');
        }
        if ((st === 'confirmed' || st === 'rejected') && tier === 'team') {
            btns.push('<button class="btn accent" data-act="new-version">새 버전 만들기</button>');
        }
        if (slot.upstreamChanged && tier === 'team') {
            btns.push('<button class="btn ok" data-act="ack-upstream">선행 검토 완료(영향 없음)</button>');
        }
        return btns.join('');
    }

    function fileRow(f, editable, slotType) {
        const dlUrl = `/api/projects/${projectId}/slots/${slotType}/files/${f.id}/download`;
        let actions = '';
        let size = '';
        if (f.assetKind === 'url') {
            actions = `<a href="${esc(f.externalUrl)}" target="_blank" rel="noopener" class="btn sm">링크 열기</a>`;
        } else {
            const ct = f.contentType || '';
            size = `<span class="t">${esc(fmtSize(f.sizeBytes))}</span>`;
            if (ct === 'application/pdf' || ct.indexOf('image/') === 0) {
                actions += `<button class="btn sm accent" data-view="${esc(f.id)}" data-ct="${esc(ct)}" data-name="${esc(f.originalName)}">보기</button>`;
            }
            actions += `<a href="${dlUrl}" download="${esc(f.originalName)}" class="btn sm">다운로드</a>`;
        }
        const del = editable ? `<button class="icon-action danger" data-del="${esc(f.id)}" title="삭제" aria-label="삭제">✕</button>` : '';
        return `
            <div class="item" style="cursor:default">
                <span class="body"><b>${esc(f.originalName)}</b></span>
                <span style="display:flex;gap:8px;align-items:center">${size}${actions}${del}</span>
            </div>`;
    }

    function renderDetail(detail) {
        const slot = detail.slot;
        const version = detail.version;
        const files = detail.files || [];
        const slotType = slot.slotType;
        const editable = canEdit && (slot.status === 'empty' || slot.status === 'draft');

        const filesHtml = files.length
            ? files.map(f => fileRow(f, editable, slotType)).join('')
            : '<p class="page-sub" style="margin:0">아직 파일이 없습니다.</p>';

        const verLine = version
            ? `v${version.versionNo} · ${esc(version.createdByName)} · ${statusPill(slot.status)}`
            : statusPill(slot.status);

        const uploadHtml = editable ? `
            <div style="margin-top:16px;display:flex;gap:8px;flex-wrap:wrap;align-items:center">
                <label class="btn accent" style="cursor:pointer">파일 업로드<input type="file" id="fileInput" hidden></label>
                <span class="page-sub" style="margin:0">PDF·이미지·문서 등 (50MB 이하)</span>
            </div>
            <form id="urlForm" style="margin-top:10px;display:flex;gap:8px;flex-wrap:wrap;align-items:center">
                <input type="text" name="label" placeholder="링크 라벨 (예: Figma)" style="flex:0 0 180px;border:1px solid var(--divider);border-radius:8px;padding:8px 10px">
                <input type="url" name="url" placeholder="https://..." style="flex:1;min-width:200px;border:1px solid var(--divider);border-radius:8px;padding:8px 10px">
                <button type="submit" class="btn sm">링크 추가</button>
            </form>` : '';

        const banner = slot.upstreamChanged
            ? `<div style="margin:0 0 14px;padding:10px 12px;border-radius:8px;background:var(--warn-soft);color:var(--warn);border:1px solid color-mix(in oklch,var(--warn) 25%,var(--surface));font-size:13px">선행 산출물이 변경되었습니다 · 영향 검토 권장</div>`
            : '';

        const actions = actionButtons(slot);
        const actionsHtml = actions
            ? `<div id="reviewActions" style="margin-top:16px;display:flex;gap:8px;flex-wrap:wrap">${actions}</div>
               <div id="rejectBox" hidden style="margin-top:10px">
                   <textarea id="rejectReason" placeholder="반려 사유 (필수)" style="width:100%;border:1px solid var(--divider);border-radius:8px;padding:9px 11px;min-height:60px"></textarea>
                   <div style="margin-top:6px;display:flex;gap:8px;justify-content:flex-end">
                       <button class="btn ghost sm" data-act="reject-cancel">취소</button>
                       <button class="btn danger-solid sm" data-act="reject-confirm">반려 확정</button>
                   </div>
               </div>
               <div id="newVersionBox" hidden style="margin-top:10px">
                   <textarea id="changeSummary" placeholder="변경 요약 (필수) — 무엇이 바뀌었나요?" style="width:100%;border:1px solid var(--divider);border-radius:8px;padding:9px 11px;min-height:60px"></textarea>
                   <div style="margin-top:6px;display:flex;gap:8px;justify-content:flex-end">
                       <button class="btn ghost sm" data-act="new-version-cancel">취소</button>
                       <button class="btn accent sm" data-act="new-version-confirm">새 버전 생성</button>
                   </div>
               </div>` : '';

        $('#slotDetail').html(`
            <div class="card-head">
                <h3>${esc(SLOT_LABEL[slotType] || slotType)}</h3>
                <span class="meta">${verLine}</span>
            </div>
            ${banner}
            <div class="card-list" id="fileList">${filesHtml}</div>
            <div id="detailErr" class="err" style="display:none;color:var(--hot);margin-top:8px"></div>
            ${uploadHtml}
            ${actionsHtml}
            <div id="activitySection" style="margin-top:22px"></div>
            <div id="commentSection" style="margin-top:18px"></div>
        `);

        wireDetail(slotType);
        loadActivity(slotType);
        loadComments(slotType);
    }

    function loadActivity(slotType) {
        $.getJSON(`/api/projects/${projectId}/slots/${slotType}/activity`).done(function (res) {
            const events = (res && res.data) || [];
            let inner;
            if (!events.length) {
                inner = '<p class="page-sub" style="margin:0">활동 내역이 없습니다.</p>';
            } else {
                inner = '<div class="card-list">' + events.map(function (e) {
                    const v = e.versionNo ? `v${e.versionNo} · ` : '';
                    const body = e.body ? `<div class="page-sub" style="margin:2px 0 0">${esc(e.body)}</div>` : '';
                    return `<div class="item" style="cursor:default;align-items:flex-start">
                        <span class="body">${v}<b>${esc(ACT_LABEL[e.eventType] || e.eventType)}</b> · ${esc(e.actorName)}${body}</span>
                        <span class="t">${esc(fmtDate(e.createdAt))}</span></div>`;
                }).join('') + '</div>';
            }
            $('#activitySection').html(`<div class="card-head" style="margin-top:0"><h3>활동</h3></div>${inner}`);
        });
    }

    function loadComments(slotType) {
        $.getJSON(`/api/projects/${projectId}/slots/${slotType}/comments`).done(function (res) {
            const comments = (res && res.data) || [];
            const list = comments.length
                ? comments.map(commentRow).join('')
                : '<p class="page-sub" style="margin:0">아직 코멘트가 없습니다.</p>';
            const form = `<form id="commentForm" style="margin-top:12px;display:flex;gap:8px;align-items:flex-start">
                    <textarea name="body" placeholder="코멘트 입력" style="flex:1;border:1px solid var(--divider);border-radius:8px;padding:9px 11px;min-height:42px"></textarea>
                    <button type="submit" class="btn primary sm">등록</button></form>`;
            $('#commentSection').html(
                `<div class="card-head" style="margin-top:0"><h3>코멘트</h3></div>
                 <div id="commentList" class="card-list">${list}</div>
                 <div id="commentErr" class="err" style="display:none;color:var(--hot);margin-top:6px"></div>
                 ${form}`);
            wireComments(slotType);
        });
    }

    function commentRow(c) {
        const mine = String(c.authorId) === myId;
        const canDelete = mine || tier === 'admin';
        const edited = c.editedAt ? ' · 수정됨' : '';
        const actions = (mine || canDelete) ? `<span class="actions" style="display:flex;gap:2px">
                ${mine ? `<button class="icon-action accent" data-cedit="${esc(c.id)}" title="수정">✎</button>` : ''}
                ${canDelete ? `<button class="icon-action danger" data-cdel="${esc(c.id)}" title="삭제">✕</button>` : ''}
            </span>` : '';
        return `<div class="item" style="cursor:default;align-items:flex-start" data-cid="${esc(c.id)}">
            <span class="body"><b>${esc(c.authorName)}</b> <span class="t">${esc(fmtDate(c.createdAt))}${edited}</span>
                <div class="comment-body" style="margin-top:3px;white-space:pre-wrap">${esc(c.body)}</div></span>
            ${actions}</div>`;
    }

    function openViewer(slotType, id, ct, name) {
        const url = `/api/projects/${projectId}/slots/${slotType}/files/${id}/download`;
        let inner;
        if (ct === 'application/pdf') {
            inner = `<iframe src="${url}" style="width:100%;height:72vh;border:0;border-radius:8px"></iframe>`;
        } else if (ct.indexOf('image/') === 0) {
            inner = `<img src="${url}" alt="${esc(name)}" style="max-width:100%;max-height:72vh;display:block;margin:0 auto">`;
        } else {
            inner = '<div style="padding:48px;text-align:center;color:var(--muted)">미리보기를 지원하지 않는 형식입니다 · 다운로드 후 확인하세요.</div>';
        }
        const $ov = $(`
            <div style="position:fixed;inset:0;background:rgba(0,0,0,.5);display:flex;align-items:center;justify-content:center;z-index:100;padding:24px">
                <div style="background:var(--surface);border-radius:12px;max-width:920px;width:100%;max-height:90vh;overflow:auto;box-shadow:var(--panel-shadow-hover)">
                    <div style="display:flex;justify-content:space-between;align-items:center;padding:14px 18px;border-bottom:1px solid var(--divider)">
                        <b>${esc(name)}</b><button class="icon-action" data-close title="닫기">✕</button>
                    </div>
                    <div style="padding:16px">${inner}</div>
                </div>
            </div>`);
        $ov.on('click', function (e) {
            if (e.target === $ov[0] || $(e.target).closest('[data-close]').length) { $ov.remove(); }
        });
        $('body').append($ov);
    }

    function postAction(slotType, action, body) {
        const $err = $('#detailErr').hide();
        $.ajax({
            url: `/api/projects/${projectId}/slots/${slotType}/${action}`,
            method: 'POST',
            contentType: 'application/json',
            data: body ? JSON.stringify(body) : undefined
        })
            .done(function () { refresh(slotType); })
            .fail(function (xhr) { $err.text(errMessage(xhr, '처리 실패')).show(); });
    }

    function wireDetail(slotType) {
        const $err = $('#detailErr');

        $('#fileInput').on('change', function () {
            const file = this.files && this.files[0];
            if (!file) { return; }
            $err.hide();
            const fd = new FormData();
            fd.append('file', file);
            $.ajax({
                url: `/api/projects/${projectId}/slots/${slotType}/files`,
                method: 'POST', data: fd, processData: false, contentType: false
            })
                .done(function () { refresh(slotType); })
                .fail(function (xhr) { $err.text(errMessage(xhr, '업로드 실패')).show(); });
        });

        $('#urlForm').on('submit', function (e) {
            e.preventDefault();
            postAction(slotType, 'urls', {
                label: $(this).find('[name=label]').val().trim(),
                url: $(this).find('[name=url]').val().trim()
            });
        });

        $('#fileList').on('click', '[data-del]', function () {
            const id = $(this).data('del');
            $err.hide();
            $.ajax({ url: `/api/projects/${projectId}/slots/${slotType}/files/${id}`, method: 'DELETE' })
                .done(function () { refresh(slotType); })
                .fail(function (xhr) { $err.text(errMessage(xhr, '삭제 실패')).show(); });
        });

        $('#fileList').on('click', '[data-view]', function () {
            openViewer(slotType, $(this).data('view'), String($(this).data('ct') || ''), String($(this).data('name') || ''));
        });

        $('#slotDetail').on('click', '[data-act]', function () {
            const act = $(this).data('act');
            if (act === 'review-request') { postAction(slotType, 'review-request'); }
            else if (act === 'review-recall') { postAction(slotType, 'review-recall'); }
            else if (act === 'confirm') { postAction(slotType, 'confirm'); }
            else if (act === 'reject') { $('#rejectBox').prop('hidden', false); }
            else if (act === 'reject-cancel') { $('#rejectBox').prop('hidden', true); }
            else if (act === 'reject-confirm') {
                const reason = $('#rejectReason').val().trim();
                if (!reason) { $err.text('반려 사유를 입력해 주세요.').show(); return; }
                postAction(slotType, 'reject', { reason: reason });
            }
            else if (act === 'new-version') { $('#newVersionBox').prop('hidden', false); }
            else if (act === 'new-version-cancel') { $('#newVersionBox').prop('hidden', true); }
            else if (act === 'new-version-confirm') {
                const summary = $('#changeSummary').val().trim();
                if (!summary) { $err.text('변경 요약을 입력해 주세요.').show(); return; }
                postAction(slotType, 'versions', { changeSummary: summary });
            }
            else if (act === 'ack-upstream') { postAction(slotType, 'ack-upstream'); }
        });
    }

    function wireComments(slotType) {
        const $err = $('#commentErr');

        $('#commentForm').on('submit', function (e) {
            e.preventDefault();
            const body = $(this).find('[name=body]').val().trim();
            if (!body) { return; }
            $err.hide();
            $.ajax({
                url: `/api/projects/${projectId}/slots/${slotType}/comments`,
                method: 'POST', contentType: 'application/json', data: JSON.stringify({ body: body })
            })
                .done(function () { loadComments(slotType); })
                .fail(function (xhr) { $err.text(errMessage(xhr, '등록 실패')).show(); });
        });

        $('#commentList').on('click', '[data-cdel]', function () {
            const id = $(this).data('cdel');
            $err.hide();
            $.ajax({ url: `/api/projects/${projectId}/comments/${id}`, method: 'DELETE' })
                .done(function () { loadComments(slotType); })
                .fail(function (xhr) { $err.text(errMessage(xhr, '삭제 실패')).show(); });
        });

        $('#commentList').on('click', '[data-cedit]', function () {
            const id = $(this).data('cedit');
            const $item = $(this).closest('[data-cid]');
            const $body = $item.find('.comment-body');
            const current = $body.text();
            if ($item.find('textarea').length) { return; }
            const $editor = $(`<div style="margin-top:6px">
                    <textarea style="width:100%;border:1px solid var(--divider);border-radius:8px;padding:8px 10px;min-height:48px"></textarea>
                    <div style="margin-top:6px;display:flex;gap:6px;justify-content:flex-end">
                        <button class="btn ghost sm" data-cedit-cancel>취소</button>
                        <button class="btn accent sm" data-cedit-save="${esc(id)}">저장</button>
                    </div></div>`);
            $editor.find('textarea').val(current);
            $body.hide().after($editor);
        });

        $('#commentList').on('click', '[data-cedit-cancel]', function () {
            const $editor = $(this).closest('div').parent();
            $editor.prev('.comment-body').show();
            $editor.remove();
        });

        $('#commentList').on('click', '[data-cedit-save]', function () {
            const id = $(this).data('cedit-save');
            const body = $(this).closest('div').parent().find('textarea').val().trim();
            if (!body) { return; }
            $err.hide();
            $.ajax({
                url: `/api/projects/${projectId}/comments/${id}`,
                method: 'PATCH', contentType: 'application/json', data: JSON.stringify({ body: body })
            })
                .done(function () { loadComments(slotType); })
                .fail(function (xhr) { $err.text(errMessage(xhr, '수정 실패')).show(); });
        });
    }

    $(function () { loadGates(); loadSlots(); });
})();
