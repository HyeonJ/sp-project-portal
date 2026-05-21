(function () {
    'use strict';
    const $root = $('#projDash');
    if (!$root.length) { return; }
    const projectId = $root.data('project-id');

    const SLOT_LABEL = { requirements: '요구사항', ia: 'IA', design: '디자인 시안', prototype: '프로토타입', figma: 'Figma' };
    const SLOT_STATUS = {
        empty: ['muted', '비어 있음'], draft: ['accent', '작성 중'], 'pending-review': ['warn', '검토 중'],
        confirmed: ['ok', '컨펌'], rejected: ['hot', '반려']
    };
    const ACT_LABEL = {
        version_created: '버전 생성', review_requested: '검토 요청', review_recalled: '검토 회수',
        confirmed: '컨펌', rejected: '반려', invalidated: '무효화', upstream_reviewed: '선행 검토 완료'
    };

    function esc(s) { return $('<div>').text(s == null ? '' : s).html(); }
    function fmtDate(iso) {
        if (!iso) { return ''; }
        const d = new Date(iso); if (isNaN(d)) { return ''; }
        const p = (n) => String(n).padStart(2, '0');
        return `${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`;
    }
    function slotStatus(s) { const x = SLOT_STATUS[s] || ['muted', s]; return `<span class="pill ${x[0]}">${esc(x[1])}</span>`; }

    function kpi(label, value, accent) {
        return `<div class="card${accent ? ' kpi accent' : ' kpi'}" style="padding:16px">
            <div class="label" style="font:500 11px/1 var(--mono);color:var(--muted);text-transform:uppercase;letter-spacing:.06em">${esc(label)}</div>
            <div style="font:700 28px/1.1 var(--font-display);margin-top:8px">${esc(value)}</div></div>`;
    }

    $.getJSON(`/api/projects/${projectId}/dashboard`).done(function (res) {
        const d = res.data;
        const p = d.project; const c = d.counts; const pr = d.progress;
        $('#dashMeta').text(`${p.client} · 상태 ${p.status}`);
        $('#dashStage').html(`현재 단계 <b>${esc(p.currentStage)} / 24</b> · 게이트 ${esc(pr.gatesPassed)}/${esc(pr.gatesTotal)} 통과 · 진행률 ${esc(pr.stagePercent)}%`);

        $('#kpiRow').html(
            kpi('검토 중', c.pendingReviews, c.pendingReviews > 0) +
            kpi('컨펌 산출물', `${c.confirmedSlots}/5`) +
            kpi('미해결 결함', c.openDefects, c.openDefects > 0) +
            kpi('TC 통과', `${c.passedTc}/${c.totalTc}`));

        $('#dashDeliverables').html((d.deliverables || []).map(function (s) {
            const ver = s.versionNo ? `v${s.versionNo}` : '—';
            return `<a class="item" href="/projects/${projectId}" style="text-decoration:none;color:var(--fg)">
                <span class="body"><b>${esc(SLOT_LABEL[s.slotType] || s.slotType)}</b> <span class="t">${ver}</span></span>${slotStatus(s.status)}</a>`;
        }).join(''));

        const acts = d.recentActivity || [];
        $('#dashActivity').html(acts.length
            ? acts.map(function (e) {
                const body = e.body ? ` · ${esc(e.body)}` : '';
                return `<div class="item" style="cursor:default;align-items:flex-start">
                    <span class="body">${esc(SLOT_LABEL[e.slotType] || e.slotType || '')} <b>${esc(ACT_LABEL[e.eventType] || e.eventType)}</b> · ${esc(e.actorName)}${body}</span>
                    <span class="t">${esc(fmtDate(e.createdAt))}</span></div>`;
            }).join('')
            : '<p class="page-sub" style="margin:0">활동이 없습니다.</p>');
    }).fail(function () {
        $('#dashMeta').text('불러오기 실패');
    });
})();
