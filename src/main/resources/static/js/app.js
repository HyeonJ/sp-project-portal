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

    let currentTier = null;

    function loadMe() {
        return $.getJSON('/api/me').done(function (res) {
            if (res && res.success && res.data) {
                currentTier = res.data.tier;
                $('#me').text(`${res.data.name} · ${res.data.email}`);
                if (currentTier === 'team' || currentTier === 'admin') {
                    $('#newProjectBtn').prop('hidden', false);
                }
            }
        }).fail(function () {
            $('#me').text('불러오기 실패');
        });
    }

    function loadProjects() {
        const $list = $('#projectList');
        $.getJSON('/api/projects')
            .done(function (res) {
                const projects = (res && res.data) || [];
                if (!projects.length) {
                    $list.html('<p class="muted">참여 중인 프로젝트가 없습니다.</p>');
                    return;
                }
                $list.empty();
                projects.forEach(function (p) {
                    $list.append(renderProject(p));
                });
            })
            .fail(function (xhr) {
                $list.html(`<p class="msg-err">${esc(errMessage(xhr, '프로젝트 조회 실패'))}</p>`);
            });
    }

    function renderProject(p) {
        const type = p.type ? `<span class="cat-tag">${esc(p.type)}</span>` : '';
        return $(`
            <article class="project-card">
                <div class="project-card-head">
                    <h3>${esc(p.name)}</h3>
                    <span class="pill pill-${esc(p.status)}">${esc(p.status)}</span>
                </div>
                <p class="muted">${esc(p.clientOrgName)} ${type}</p>
                <p class="project-meta">단계 ${esc(p.currentStage)} / 24</p>
            </article>
        `);
    }

    function wireCreate() {
        const $card = $('#createCard');
        const $form = $('#createForm');
        const $err = $('#createErr');

        $('#newProjectBtn').on('click', function () {
            $card.prop('hidden', false);
            $form[0].reset();
            $err.prop('hidden', true);
        });
        $('#cancelCreate').on('click', function () {
            $card.prop('hidden', true);
        });

        $form.on('submit', function (e) {
            e.preventDefault();
            $err.prop('hidden', true);
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
                    $err.text(errMessage(xhr, '생성 실패')).prop('hidden', false);
                })
                .always(function () {
                    $submit.prop('disabled', false);
                });
        });
    }

    $(function () {
        loadMe();
        loadProjects();
        wireCreate();
    });
})();
