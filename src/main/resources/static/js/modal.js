/* 공용 모달 — 프로토타입 openModal/closeModals 이식. window.SPModal 노출. */
window.SPModal = (function () {
    'use strict';

    function close() {
        $('#modalRoot').remove();
        $(document).off('keydown.spmodal');
    }

    function open(contentHtml, lg) {
        close();
        const $root = $(`<div id="modalRoot"><div class="modal-backdrop open"><div class="modal ${lg ? 'lg' : ''}">${contentHtml}</div></div></div>`);
        $('body').append($root);
        $root.on('click', function (e) {
            if ($(e.target).hasClass('modal-backdrop') || $(e.target).closest('[data-action="close-modal"]').length) {
                close();
            }
        });
        $(document).on('keydown.spmodal', function (e) {
            if (e.key === 'Escape') { close(); }
        });
        return $root;
    }

    function head(title) {
        return `<div class="modal-head"><h2>${title}</h2><button class="close" type="button" data-action="close-modal">✕</button></div>`;
    }

    return { open: open, close: close, head: head };
})();
