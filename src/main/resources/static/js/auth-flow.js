(function () {
    'use strict';

    function esc(s) { return $('<div>').text(s == null ? '' : s).html(); }
    function errMessage(xhr, f) { const r = xhr.responseJSON; return (r && r.message) ? r.message : (f || `오류 (HTTP ${xhr.status})`); }
    function qparam(k) { return new URLSearchParams(location.search).get(k); }

    // 초대 수락
    const $accept = $('#inviteAcceptForm');
    if ($accept.length) {
        $accept.on('submit', function (e) {
            e.preventDefault();
            const $err = $('#acceptErr').hide();
            const token = qparam('token');
            if (!token) { $err.text('초대 토큰이 없습니다.').show(); return; }
            $.ajax({
                url: '/api/invite/accept', method: 'POST', contentType: 'application/json',
                data: JSON.stringify({ token: token, password: $accept.find('[name=password]').val() })
            })
                .done(function () { window.location.href = '/login?accepted'; })
                .fail(function (xhr) { $err.text(errMessage(xhr, '수락 실패')).show(); });
        });
    }

    // 재설정 링크 요청
    const $req = $('#resetRequestForm');
    if ($req.length) {
        $req.on('submit', function (e) {
            e.preventDefault();
            const $msg = $('#reqMsg');
            $.ajax({
                url: '/api/password/reset-request', method: 'POST', contentType: 'application/json',
                data: JSON.stringify({ email: $req.find('[name=email]').val().trim() })
            })
                .done(function (res) {
                    const url = res.data && res.data.resetUrl;
                    if (url) {
                        $msg.css('color', 'var(--ok)').html('재설정 링크: <a href="' + esc(url) + '">' + esc(url) + '</a>').show();
                    } else {
                        $msg.css('color', 'var(--muted)').text('해당 이메일로 재설정 안내를 보냈습니다(가입된 경우).').show();
                    }
                })
                .fail(function (xhr) { $msg.css('color', 'var(--hot)').text(errMessage(xhr, '요청 실패')).show(); });
        });
    }

    // 새 비밀번호 설정
    const $confirm = $('#resetConfirmForm');
    if ($confirm.length) {
        $confirm.on('submit', function (e) {
            e.preventDefault();
            const $err = $('#confirmErr').hide();
            const token = qparam('token');
            if (!token) { $err.text('토큰이 없습니다.').show(); return; }
            $.ajax({
                url: '/api/password/reset', method: 'POST', contentType: 'application/json',
                data: JSON.stringify({ token: token, password: $confirm.find('[name=password]').val() })
            })
                .done(function () { window.location.href = '/login?reset'; })
                .fail(function (xhr) { $err.text(errMessage(xhr, '변경 실패')).show(); });
        });
    }
})();
