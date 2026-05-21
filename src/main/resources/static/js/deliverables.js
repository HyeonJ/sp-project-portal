(function () {
    'use strict';

    const $root = $('#deliverables');
    if (!$root.length) {
        return;
    }
    const projectId = $root.data('project-id');
    const canEdit = $root.data('can-edit') === true || $root.data('can-edit') === 'true';
    const tier = String($root.data('tier') || '');

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
        const $card = $(`
            <div class="deliverable" data-slot="${esc(slot.slotType)}">
                <div style="display:flex;justify-content:space-between;width:100%;align-items:flex-start;gap:8px">
                    <b style="font-size:14px">${esc(SLOT_LABEL[slot.slotType] || slot.slotType)}</b>
                    ${statusPill(slot.status)}
                </div>
                <span style="margin-top:auto;font:500 11px/1 var(--mono);color:var(--muted)">${ver}</span>
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
        if (st === 'draft' && tier === 'team') {
            return '<button class="btn primary" data-act="review-request">검토 요청</button>';
        }
        if (st === 'pending-review' && tier === 'team') {
            return '<button class="btn" data-act="review-recall">검토 요청 회수</button>';
        }
        if (st === 'pending-review' && tier === 'client') {
            return '<button class="btn ok" data-act="confirm">컨펌</button>'
                + '<button class="btn danger" data-act="reject">반려</button>';
        }
        return '';
    }

    function renderDetail(detail) {
        const slot = detail.slot;
        const version = detail.version;
        const files = detail.files || [];
        const slotType = slot.slotType;
        const editable = canEdit && (slot.status === 'empty' || slot.status === 'draft');

        const filesHtml = files.length
            ? files.map(f => fileRow(f, editable)).join('')
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

        const actions = actionButtons(slot);
        const actionsHtml = actions
            ? `<div id="reviewActions" style="margin-top:16px;display:flex;gap:8px;flex-wrap:wrap">${actions}</div>
               <div id="rejectBox" hidden style="margin-top:10px">
                   <textarea id="rejectReason" placeholder="반려 사유 (필수)" style="width:100%;border:1px solid var(--divider);border-radius:8px;padding:9px 11px;min-height:60px"></textarea>
                   <div style="margin-top:6px;display:flex;gap:8px;justify-content:flex-end">
                       <button class="btn ghost sm" data-act="reject-cancel">취소</button>
                       <button class="btn danger-solid sm" data-act="reject-confirm">반려 확정</button>
                   </div>
               </div>` : '';

        $('#slotDetail').html(`
            <div class="card-head">
                <h3>${esc(SLOT_LABEL[slotType] || slotType)}</h3>
                <span class="meta">${verLine}</span>
            </div>
            <div class="card-list" id="fileList">${filesHtml}</div>
            <div id="detailErr" class="err" style="display:none;color:var(--hot);margin-top:8px"></div>
            ${uploadHtml}
            ${actionsHtml}
        `);

        wireDetail(slotType);
    }

    function fileRow(f, editable) {
        const meta = f.assetKind === 'url'
            ? `<a href="${esc(f.externalUrl)}" target="_blank" rel="noopener">링크 열기</a>`
            : `<span class="t">${esc(fmtSize(f.sizeBytes))}</span>`;
        const del = editable
            ? `<button class="icon-action danger" data-del="${esc(f.id)}" title="삭제" aria-label="삭제">✕</button>`
            : '';
        return `
            <div class="item" style="cursor:default">
                <span class="body"><b>${esc(f.originalName)}</b></span>
                <span style="display:flex;gap:10px;align-items:center">${meta}${del}</span>
            </div>`;
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
        });
    }

    $(function () { loadGates(); loadSlots(); });
})();
