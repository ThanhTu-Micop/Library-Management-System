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

    // Show inline validation error under a field.
    function showFieldError(inputId, message) {
        var input = document.getElementById(inputId);
        if (!input) return;
        input.classList.add('error');
        var existing = input.parentElement.querySelector('.field-error');
        if (existing) {
            existing.textContent = message;
            existing.setAttribute('role', 'alert');
            existing.setAttribute('aria-live', 'polite');
        } else {
            var err = document.createElement('p');
            err.className = 'field-error';
            err.textContent = message;
            err.setAttribute('role', 'alert');
            err.setAttribute('aria-live', 'polite');
            input.parentElement.appendChild(err);
        }
        input.setAttribute('aria-invalid', 'true');
    }

    // Clear inline validation errors.
    function clearFieldErrors() {
        document.querySelectorAll('.field-error').forEach(function (el) { el.remove(); });
        document.querySelectorAll('.error').forEach(function (el) { el.classList.remove('error'); });
        document.querySelectorAll('[aria-invalid="true"]').forEach(function (el) { el.removeAttribute('aria-invalid'); });
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

    // Toggle password visibility (if used).
    function initTogglePassword() {
        document.querySelectorAll('[data-toggle-password]').forEach(function (btn) {
            btn.addEventListener('click', function () {
                var targetId = btn.getAttribute('data-toggle-password');
                var input = document.getElementById(targetId);
                if (!input) return;
                if (input.type === 'password') {
                    input.type = 'text';
                    btn.textContent = 'Hide';
                } else {
                    input.type = 'password';
                    btn.textContent = 'Show';
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
        initTogglePassword();
        initAutoDismiss();
        initTableRows();
    });
})();
