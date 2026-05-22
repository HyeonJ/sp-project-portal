(function () {
    'use strict';
    const $page = $('#projectNewPage');
    if (!$page.length) { return; }

    function errMessage(xhr, f) { const r = xhr.responseJSON; return (r && r.message) ? r.message : (f || `오류 (HTTP ${xhr.status})`); }

    $('#createBtn').on('click', function () {
        const f = document.getElementById('newForm');
        const $err = $('#newErr').hide();
        const payload = {
            name: f.name.value.trim(),
            clientOrgName: f.clientOrgName.value.trim(),
            type: f.type.value || null,
            startDate: f.startDate.value || null,
            endDate: f.endDate.value || null,
            description: f.description.value.trim() || null
        };
        if (!payload.name || !payload.clientOrgName) { $err.text('프로젝트명과 고객사명을 입력하세요.').show(); return; }
        const $btn = $(this).prop('disabled', true);
        $.ajax({ url: '/api/projects', method: 'POST', contentType: 'application/json', data: JSON.stringify(payload) })
            .done(function (res) {
                const id = res.data && res.data.id;
                window.location = id ? `/projects/${id}` : '/';
            })
            .fail(function (xhr) { $err.text(errMessage(xhr, '생성 실패')).show(); $btn.prop('disabled', false); });
    });
})();
