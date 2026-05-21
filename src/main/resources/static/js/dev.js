(function () {
    'use strict';

    const $root = $('#devPage');
    if (!$root.length) {
        return;
    }
    const projectId = $root.data('project-id');
    const tier = String($root.data('tier') || '');
    const REQUIRED = [9, 11, 13, 15];
    const GATE_LABEL = { 9: '요구사항', 11: 'IA', 13: '디자인', 15: '프로토타입' };
    const GATE_STATUS = {
        'pass': { txt: '통과', cls: 'ok' }, 'wait': { txt: '대기', cls: 'warn' }, 'lock': { txt: '잠김', cls: 'muted' }
    };

    function esc(s) { return $('<div>').text(s == null ? '' : s).html(); }

    function errMessage(xhr, fallback) {
        const res = xhr.responseJSON;
        return (res && res.message) ? res.message : (fallback || `오류 (HTTP ${xhr.status})`);
    }

    function fmtDate(iso) {
        if (!iso) { return ''; }
        const d = new Date(iso);
        if (isNaN(d)) { return ''; }
        const p = (n) => String(n).padStart(2, '0');
        return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`;
    }

    function loadStatus() {
        $.getJSON(`/api/projects/${projectId}/gates`).done(function (res) {
            const gates = (res && res.data) || [];
            const byStage = {};
            gates.forEach(g => { byStage[g.gateStage] = g.status; });
            const $pre = $('#precondition').empty();
            let ready = true;
            REQUIRED.forEach(function (stage) {
                const st = byStage[stage] || 'lock';
                if (st !== 'pass') { ready = false; }
                const s = GATE_STATUS[st] || { txt: st, cls: 'muted' };
                $pre.append(`<span class="pill ${s.cls}">G${stage} ${esc(GATE_LABEL[stage])} · ${s.txt}</span>`);
            });
            const canTrigger = ready && tier === 'team';
            $('#readyMeta').text(ready ? '사전조건 충족' : '미충족');
            $('#devStartBtn').prop('disabled', !canTrigger);
            $('#devMsg').text(
                ready ? (tier === 'team' ? '' : '개발 시작은 프로젝트팀만 가능합니다.')
                      : '게이트 4개(요구사항·IA·디자인·프로토타입)를 모두 통과해야 합니다.');
        });
    }

    function loadRuns() {
        $.getJSON(`/api/projects/${projectId}/dev-runs`).done(function (res) {
            const runs = (res && res.data) || [];
            const $list = $('#runList');
            if (!runs.length) {
                $list.html('<p class="page-sub" style="margin:0">실행 이력이 없습니다.</p>');
                return;
            }
            $list.html(runs.map(function (r) {
                const pill = r.result === 'success'
                    ? '<span class="pill ok">성공</span>' : '<span class="pill hot">실패</span>';
                const reason = r.reason ? ` · ${esc(r.reason)}` : '';
                return `<div class="item" style="cursor:default">
                    <span class="body">${pill} <b>${esc(r.triggeredByName || '')}</b>${reason}</span>
                    <span class="t">${esc(fmtDate(r.createdAt))}</span></div>`;
            }).join(''));
        });
    }

    $('#devStartBtn').on('click', function () {
        const $btn = $(this).prop('disabled', true);
        $('#devMsg').text('');
        $.ajax({ url: `/api/projects/${projectId}/dev-runs`, method: 'POST' })
            .done(function () {
                $('#devMsg').css('color', 'var(--ok)').text('개발(코드 자동 생성)을 시작했습니다.');
                loadRuns();
            })
            .fail(function (xhr) {
                $('#devMsg').css('color', 'var(--hot)').text(errMessage(xhr, '시작 실패'));
            })
            .always(function () { $btn.prop('disabled', false); loadStatus(); });
    });

    $(function () { loadStatus(); loadRuns(); });
})();
