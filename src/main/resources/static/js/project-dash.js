(function () {
    'use strict';
    const $m = $('main.main[data-project-id]');
    if (!$m.length) { return; }
    const pid = $m.data('project-id');

    // 대시보드 액션 버튼 → 해당 워크플로우 페이지로 이동
    $m.on('click', '[data-action]', function () {
        const a = $(this).data('action');
        if (a === 'open-dev') {
            window.location = `/projects/${pid}/dev`;
        } else if (a === 'open-review' || a === 'open-approve' || a === 'export-locked') {
            window.location = `/projects/${pid}/deliverables`;
        }
    });
})();
