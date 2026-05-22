(function () {
    'use strict';
    const $bell = $('#notifBell');
    if (!$bell.length) { return; }

    function esc(s) { return $('<div>').text(s == null ? '' : s).html(); }
    function fmtDate(iso) {
        if (!iso) { return ''; }
        const d = new Date(iso);
        if (isNaN(d)) { return ''; }
        const p = (n) => String(n).padStart(2, '0');
        return `${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`;
    }

    let $panel = null;

    function refreshBadge() {
        $.getJSON('/api/notifications').done(function (res) {
            const unread = (res.data && res.data.unread) || 0;
            const $dot = $('#notifDot');
            if (unread > 0) {
                $dot.text(unread).prop('hidden', false).attr('style',
                    'top:2px;right:2px;width:16px;height:16px;border-radius:99px;background:var(--hot);color:#fff;font:600 10px/16px var(--font);text-align:center');
            } else {
                $dot.prop('hidden', true);
            }
        });
    }

    function closePanel() { if ($panel) { $panel.remove(); $panel = null; } }

    function openPanel() {
        $.getJSON('/api/notifications').done(function (res) {
            const items = (res.data && res.data.items) || [];
            const rows = items.length
                ? items.map(n => `
                    <a class="dd-row" href="${esc(n.link || '#')}" data-id="${esc(n.id)}"
                       style="display:block;padding:10px 14px;border-bottom:1px solid var(--divider);text-decoration:none;color:var(--fg);${n.read ? '' : 'background:var(--accent-soft)'}">
                        <div style="font-size:13px">${esc(n.body)}</div>
                        <div class="t" style="font:500 10px/1 var(--mono);color:var(--muted);margin-top:3px">${esc(fmtDate(n.createdAt))}</div>
                    </a>`).join('')
                : '<div class="page-sub" style="padding:16px;text-align:center">알림이 없습니다.</div>';
            $panel = $(`<div style="position:fixed;top:54px;right:24px;width:320px;max-height:70vh;overflow:auto;background:var(--surface);border:1px solid var(--layer-border);border-radius:12px;box-shadow:var(--panel-shadow-hover);z-index:120">
                <div style="display:flex;justify-content:space-between;align-items:center;padding:10px 14px;border-bottom:1px solid var(--divider)">
                    <b style="font-size:13px">알림</b><button class="btn ghost sm" id="notifReadAll" type="button">모두 읽음</button>
                </div>${rows}</div>`);
            $('body').append($panel);

            $panel.on('click', '#notifReadAll', function (e) {
                e.preventDefault();
                $.ajax({ url: '/api/notifications/read-all', method: 'POST' }).always(function () { closePanel(); refreshBadge(); });
            });
            $panel.on('click', '.dd-row', function (e) {
                const id = $(this).data('id');
                const href = $(this).attr('href');
                e.preventDefault();
                $.ajax({ url: `/api/notifications/${id}/read`, method: 'POST' }).always(function () {
                    if (href && href !== '#') { window.location.href = href; } else { closePanel(); refreshBadge(); }
                });
            });
        });
    }

    $bell.on('click', function (e) {
        e.stopPropagation();
        if ($panel) { closePanel(); } else { openPanel(); }
    });

    // 계정 드롭다운 (acct 클릭) — 계정 설정 / 로그아웃
    let $acctMenu = null;
    function closeAcct() { if ($acctMenu) { $acctMenu.remove(); $acctMenu = null; } }
    $('.acct[data-action="acct"]').on('click', function (e) {
        e.stopPropagation();
        if ($acctMenu) { closeAcct(); return; }
        $acctMenu = $(`<div class="dropdown" style="position:fixed;top:54px;right:24px;min-width:160px;background:var(--surface);border:1px solid var(--layer-border);border-radius:12px;box-shadow:var(--panel-shadow-hover);z-index:120;overflow:hidden">
                <a href="/account" style="display:block;padding:10px 14px;text-decoration:none;color:var(--fg);font-size:13px;border-bottom:1px solid var(--divider)">계정 설정</a>
                <button type="button" id="acctLogout" style="display:block;width:100%;text-align:left;padding:10px 14px;background:none;border:0;color:var(--fg);font-size:13px;cursor:pointer">로그아웃</button>
            </div>`);
        $('body').append($acctMenu);
        $acctMenu.on('click', '#acctLogout', function () {
            const f = document.getElementById('logoutForm');
            if (f) { f.submit(); }
        });
    });

    // 상단바 검색 pill → 통합 검색 페이지
    $('[data-action="open-search"]').on('click', function () { window.location = '/search'; });

    // 프로젝트 전환 드롭다운
    const $projMenu = $('#projSwitchMenu');
    $('.proj-switcher[data-action="proj-switch"]').on('click', function (e) {
        if ($(e.target).closest('#projSwitchMenu').length) { return; } // 메뉴 항목 클릭 → 이동
        e.stopPropagation();
        $projMenu.prop('hidden', !$projMenu.prop('hidden'));
    });

    $(document).on('click', function (e) {
        if ($panel && !$(e.target).closest('#notifBell').length && !$(e.target).closest($panel).length) {
            closePanel();
        }
        if ($acctMenu && !$(e.target).closest('.acct').length && !$(e.target).closest($acctMenu).length) {
            closeAcct();
        }
        if ($projMenu.length && !$projMenu.prop('hidden') && !$(e.target).closest('.proj-switcher').length) {
            $projMenu.prop('hidden', true);
        }
    });

    $(function () { refreshBadge(); });
})();
