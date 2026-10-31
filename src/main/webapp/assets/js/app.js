/* Library Management System - Person 1 UI/UX JavaScript */
/* No framework; vanilla JS only. */

(function () {
    'use strict';

    // Disable submit buttons after click to avoid double submission.
    function initFormGuard() {
        document.querySelectorAll('form[data-guard]').forEach(function (form) {
            form.addEventListener('submit', function () {
                var btns = form.querySelectorAll('button[type="submit"]');
                btns.forEach(function (btn) {
                    btn.disabled = true;
                });
            });
        });
    }

    // Confirm before destructive action.
    function initConfirmLinks() {
        document.querySelectorAll('[data-confirm]').forEach(function (el) {
            el.addEventListener('click', function (e) {
                var msg = el.getAttribute('data-confirm') || 'Are you sure you want to perform this action?';
                if (!confirm(msg)) {
                    e.preventDefault();
                    return false;
                }
            });
        });
    }

    // Auto-dismiss flash messages after 5 seconds.
    function initAutoDismiss() {
        setTimeout(function () {
            document.querySelectorAll('.flash').forEach(function (flash) {
                flash.style.transition = 'opacity 0.4s';
                flash.style.opacity = '0';
                setTimeout(function () { flash.remove(); }, 400);
            });
        }, 5000);
    }

    // Table row selection highlight (accessible).
    function initTableRows() {
        document.querySelectorAll('table tbody tr').forEach(function (row) {
            row.addEventListener('keydown', function (e) {
                if (e.key === 'Enter' || e.key === ' ') {
                    var link = row.querySelector('a');
                    if (link) link.click();
                }
            });
            row.setAttribute('tabindex', '0');
            row.setAttribute('role', 'row');
        });
    }

    document.addEventListener('DOMContentLoaded', function () {
        initFormGuard();
        initConfirmLinks();
        initAutoDismiss();
        initTableRows();
    });
})();
