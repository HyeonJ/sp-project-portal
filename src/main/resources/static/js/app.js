(function () {
    'use strict';

    // CSRF: 모든 비-GET AJAX에 세션 토큰 헤더 부착 (Spring Security 검증)
    const token = $('meta[name="_csrf"]').attr('content');
    const header = $('meta[name="_csrf_header"]').attr('content');
    if (token && header) {
        $.ajaxSetup({
            beforeSend: function (xhr) {
                xhr.setRequestHeader(header, token);
            }
        });
    }

    function loadMe() {
        const $me = $('#me');
        if (!$me.length) {
            return;
        }
        $.getJSON('/api/me')
            .done(function (res) {
                if (res && res.success && res.data) {
                    $me.text(`${res.data.name} (${res.data.email}) · ${res.data.tier}`);
                } else {
                    $me.text('알 수 없음');
                }
            })
            .fail(function () {
                $me.text('불러오기 실패');
            });
    }

    function wireEcho() {
        const $btn = $('#echoBtn');
        const $out = $('#out');
        if (!$btn.length) {
            return;
        }
        $btn.on('click', function () {
            $btn.prop('disabled', true);
            $.ajax({
                url: '/api/echo',
                method: 'POST',
                contentType: 'application/json',
                data: JSON.stringify({ ping: 'hello', at: new Date().toISOString() })
            })
                .done(function (res) {
                    $out.text(JSON.stringify(res, null, 2));
                })
                .fail(function (xhr) {
                    $out.text(`실패: HTTP ${xhr.status}`);
                })
                .always(function () {
                    $btn.prop('disabled', false);
                });
        });
    }

    $(function () {
        loadMe();
        wireEcho();
    });
})();
