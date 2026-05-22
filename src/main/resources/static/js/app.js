(function () {
    'use strict';

    // CSRF: 모든 비-GET AJAX에 세션 토큰 헤더 부착
    const token = $('meta[name="_csrf"]').attr('content');
    const header = $('meta[name="_csrf_header"]').attr('content');
    if (token && header) {
        $.ajaxSetup({
            beforeSend: function (xhr) {
                xhr.setRequestHeader(header, token);
            }
        });
    }

    function esc(s) {
        return $('<div>').text(s == null ? '' : s).html();
    }

    function errMessage(xhr, fallback) {
        const res = xhr.responseJSON;
        return (res && res.message) ? res.message : (fallback || `오류 (HTTP ${xhr.status})`);
    }

    function statusPill(status) {
        const cls = status === 'active' ? 'accent' : (status === 'completed' ? 'ok' : 'muted');
        return `<span class="pill ${cls}">${esc(status)}</span>`;
    }

    function loadProjects() {
        const $list = $('#projectList');
        if (!$list.length) {
            return;
        }
        $.getJSON('/api/projects')
            .done(function (res) {
                const projects = (res && res.data) || [];
                if (!projects.length) {
                    $list.html('<p class="page-sub">참여 중인 프로젝트가 없습니다.</p>');
                    return;
                }
                $list.empty();
                projects.forEach(function (p) {
                    $list.append(renderProject(p));
                });
            })
            .fail(function (xhr) {
                $list.html(`<p class="err" style="color:var(--hot)">${esc(errMessage(xhr, '프로젝트 조회 실패'))}</p>`);
            });
    }

    function renderProject(p) {
        const type = p.type ? ` · ${esc(p.type)}` : '';
        const stage = p.currentStage || 1;
        const pct = Math.round(stage * 100 / 20);
        const pill = p.status === 'completed'
            ? '<span class="pill ok">완료</span>'
            : (pct >= 90 ? '<span class="pill warn">검수 단계</span>' : '<span class="pill accent">진행 중</span>');
        const $card = $(`
            <div class="proj-card" data-id="${esc(p.id)}">
                <div class="top">
                    <div>
                        <h3>${esc(p.name)}</h3>
                        <div class="client">${esc(p.clientOrgName)}${type}</div>
                    </div>
                    ${pill}
                </div>
                <div class="stage">단계 <b>${stage}/20</b></div>
                <div class="progress"><div class="bar"><i style="width:${pct}%"></i></div></div>
            </div>
        `);
        $card.on('click', function () { window.location.href = `/projects/${p.id}`; });
        return $card;
    }

    function wireCreate() {
        const $card = $('#createCard');
        const $form = $('#createForm');
        if (!$card.length) {
            return;
        }
        const $err = $('#createErr');

        function open() {
            $card.prop('hidden', false);
            $form[0].reset();
            $err.hide();
            $form.find('[name=name]').trigger('focus');
        }

        $('#newProjectBtn').on('click', open);
        $('#navNewProject').on('click', open);
        $('#cancelCreate').on('click', function () {
            $card.prop('hidden', true);
        });

        $form.on('submit', function (e) {
            e.preventDefault();
            $err.hide();
            const payload = {
                name: $form.find('[name=name]').val().trim(),
                clientOrgName: $form.find('[name=clientOrgName]').val().trim(),
                type: $form.find('[name=type]').val() || null,
                description: $form.find('[name=description]').val().trim() || null,
                startDate: $form.find('[name=startDate]').val() || null,
                endDate: $form.find('[name=endDate]').val() || null
            };
            const $submit = $form.find('button[type=submit]').prop('disabled', true);
            $.ajax({
                url: '/api/projects',
                method: 'POST',
                contentType: 'application/json',
                data: JSON.stringify(payload)
            })
                .done(function () {
                    $card.prop('hidden', true);
                    loadProjects();
                })
                .fail(function (xhr) {
                    $err.text(errMessage(xhr, '생성 실패')).show();
                })
                .always(function () {
                    $submit.prop('disabled', false);
                });
        });
    }

    $(function () {
        loadProjects();
        wireCreate();
    });
})();
