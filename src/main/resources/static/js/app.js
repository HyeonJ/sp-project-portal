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
        return $(`
            <div class="card" data-id="${esc(p.id)}">
                <div class="card-head" style="border:0;padding:0;margin-bottom:10px">
                    <h3>${esc(p.name)}</h3>
                    ${statusPill(p.status)}
                </div>
                <p class="page-sub" style="margin:0">${esc(p.clientOrgName)}${type}</p>
                <p style="margin:8px 0 0;font:500 12px/1 var(--mono);color:var(--muted)">단계 ${esc(p.currentStage)} / 24</p>
            </div>
        `);
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
