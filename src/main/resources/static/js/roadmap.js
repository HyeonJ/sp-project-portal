(function () {
    'use strict';
    const $root = $('#roadmapPage');
    if (!$root.length) { return; }
    const projectId = $root.data('project-id');

    // 24단계 요약 라벨 (flow-diagram 기준)
    const STEPS = {
        1: '프로젝트 생성', 2: '팀 구성', 3: '접근 구분', 4: '고객사 초대', 5: '고객 온보딩',
        6: '요구사항 파일 등록', 7: '요구사항 협의', 8: '요구사항 보완', 9: '요구사항 컨펌(G9)',
        10: 'IA 작성', 11: 'IA 컨펌(G11)', 12: '디자인 시안', 13: '디자인 컨펌(G13)',
        14: '프로토타입', 15: '프로토타입 컨펌(G15)', 16: 'Figma 정제', 17: 'Figma 핸드오프',
        18: '개발(코드 자동 생성)', 19: 'TC 작성', 20: 'UAT 실행', 21: '결함 수정',
        22: 'UAT 승인(G22)', 23: '배포', 24: '완료'
    };
    const GATES = { 9: true, 11: true, 13: true, 15: true, 22: true };
    const GATE_STATUS = { pass: ['ok', '통과'], wait: ['warn', '대기'], lock: ['muted', '잠김'] };

    function esc(s) { return $('<div>').text(s == null ? '' : s).html(); }

    let gates = {};
    let currentStage = 1;
    let projStatus = 'active';

    function render() {
        const rows = [];
        for (let n = 1; n <= 24; n++) {
            const done = n < currentStage || projStatus === 'completed';
            const current = n === currentStage && projStatus !== 'completed';
            const dot = done ? '<span class="pill ok">완료</span>'
                : current ? '<span class="pill accent">진행 중</span>'
                    : '<span class="pill muted">대기</span>';
            let gate = '';
            if (GATES[n]) {
                const gs = GATE_STATUS[gates[n]] || ['muted', '잠김'];
                gate = `<span class="pill ${gs[0]}" style="margin-left:6px">게이트 ${gs[1]}</span>`;
            }
            rows.push(`<div class="item" style="cursor:default">
                <span class="body"><b style="font-family:var(--mono)">${n}</b> ${esc(STEPS[n] || ('단계 ' + n))}</span>
                <span style="display:flex;gap:4px;align-items:center">${dot}${gate}</span></div>`);
        }
        $('#roadmapList').html(rows.join(''));
    }

    $.when(
        $.getJSON(`/api/projects/${projectId}`),
        $.getJSON(`/api/projects/${projectId}/gates`)
    ).done(function (projRes, gateRes) {
        const p = projRes[0].data;
        currentStage = p.currentStage;
        projStatus = p.status;
        (gateRes[0].data || []).forEach(function (g) { gates[g.gateStage] = g.status; });
        $('#roadmapMeta').html(`현재 단계 <b>${esc(currentStage)} / 24</b> · 상태 ${esc(projStatus)}`);
        render();
    }).fail(function () { $('#roadmapMeta').text('불러오기 실패'); });
})();
