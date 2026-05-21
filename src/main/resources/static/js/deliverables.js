(function () {
    'use strict';

    const $root = $('#deliverables');
    if (!$root.length) {
        return;
    }
    const projectId = $root.data('project-id');
    const canEdit = $root.data('can-edit') === true || $root.data('can-edit') === 'true';

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

    function loadSlots() {
        $.getJSON(`/api/projects/${projectId}/slots`)
            .done(function (res) {
                const slots = (res && res.data) || [];
                const $grid = $('#slotGrid').empty();
                slots.forEach(function (slot) {
                    $grid.append(renderSlotCard(slot));
                });
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
            .done(function (res) {
                renderDetail(res.data);
            })
            .fail(function (xhr) {
                $detail.html(`<p class="err" style="color:var(--hot)">${esc(errMessage(xhr, '상세 조회 실패'))}</p>`);
            });
    }

    function renderDetail(detail) {
        const slot = detail.slot;
        const version = detail.version;
        const files = detail.files || [];
        const slotType = slot.slotType;
        const editable = canEdit && (slot.status === 'empty' || slot.status === 'draft');

        let filesHtml = files.length
            ? files.map(f => fileRow(f, editable, slotType)).join('')
            : '<p class="page-sub" style="margin:0">아직 파일이 없습니다.</p>';

        const verLine = version
            ? `v${version.versionNo} · ${esc(version.createdByName)} · ${statusPill(slot.status)}`
            : statusPill(slot.status);

        const uploadHtml = editable ? `
            <div style="margin-top:16px;display:flex;gap:8px;flex-wrap:wrap;align-items:center">
                <label class="btn accent" style="cursor:pointer">
                    파일 업로드<input type="file" id="fileInput" hidden>
                </label>
                <span class="page-sub" style="margin:0">PDF·이미지·문서 등 (50MB 이하)</span>
            </div>
            <form id="urlForm" style="margin-top:10px;display:flex;gap:8px;flex-wrap:wrap;align-items:center">
                <input type="text" name="label" placeholder="링크 라벨 (예: Figma)" style="flex:0 0 180px;border:1px solid var(--divider);border-radius:8px;padding:8px 10px">
                <input type="url" name="url" placeholder="https://..." style="flex:1;min-width:200px;border:1px solid var(--divider);border-radius:8px;padding:8px 10px">
                <button type="submit" class="btn sm">링크 추가</button>
            </form>
        ` : (canEdit ? '<p class="page-sub" style="margin-top:14px">검토 중·컨펌·반려 버전은 잠겨 있습니다.</p>' : '');

        $('#slotDetail').html(`
            <div class="card-head">
                <h3>${esc(SLOT_LABEL[slotType] || slotType)}</h3>
                <span class="meta">${verLine}</span>
            </div>
            <div class="card-list" id="fileList">${filesHtml}</div>
            <div id="detailErr" class="err" style="display:none;color:var(--hot);margin-top:8px"></div>
            ${uploadHtml}
        `);

        wireDetail(slotType);
    }

    function fileRow(f, editable, slotType) {
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
                method: 'POST',
                data: fd,
                processData: false,
                contentType: false
            })
                .done(function () { openSlot(slotType); loadSlots(); })
                .fail(function (xhr) { $err.text(errMessage(xhr, '업로드 실패')).show(); });
        });

        $('#urlForm').on('submit', function (e) {
            e.preventDefault();
            $err.hide();
            const payload = {
                label: $(this).find('[name=label]').val().trim(),
                url: $(this).find('[name=url]').val().trim()
            };
            $.ajax({
                url: `/api/projects/${projectId}/slots/${slotType}/urls`,
                method: 'POST',
                contentType: 'application/json',
                data: JSON.stringify(payload)
            })
                .done(function () { openSlot(slotType); loadSlots(); })
                .fail(function (xhr) { $err.text(errMessage(xhr, '링크 추가 실패')).show(); });
        });

        $('#fileList').on('click', '[data-del]', function () {
            const id = $(this).data('del');
            $err.hide();
            $.ajax({
                url: `/api/projects/${projectId}/slots/${slotType}/files/${id}`,
                method: 'DELETE'
            })
                .done(function () { openSlot(slotType); loadSlots(); })
                .fail(function (xhr) { $err.text(errMessage(xhr, '삭제 실패')).show(); });
        });
    }

    $(function () { loadSlots(); });
})();
