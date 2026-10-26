-- Manual PK/FK/relation test for the Library Management System.
-- Runs against library_test inside one transaction and rolls back.
-- Expected SQLSTATE codes:
--   23505 unique_violation, 23503 foreign_key_violation,
--   23502 not_null_violation, 23514 check_violation, 22001 string_data_right_truncation

BEGIN;

CREATE TEMP TABLE test_results (
    id SERIAL PRIMARY KEY,
    label TEXT NOT NULL,
    expected TEXT NOT NULL,
    actual TEXT NOT NULL,
    status TEXT NOT NULL
);

CREATE OR REPLACE FUNCTION pg_temp.expect_error(p_sql TEXT, p_state TEXT, p_label TEXT)
RETURNS VOID AS $$
DECLARE
    v_state TEXT;
BEGIN
    BEGIN
        EXECUTE p_sql;
        INSERT INTO test_results(label, expected, actual, status)
        VALUES (p_label, p_state, 'NO_ERROR', 'FAIL');
    EXCEPTION WHEN OTHERS THEN
        GET STACKED DIAGNOSTICS v_state = RETURNED_SQLSTATE;
        INSERT INTO test_results(label, expected, actual, status)
        VALUES (p_label, p_state, v_state,
                CASE WHEN v_state = p_state THEN 'PASS' ELSE 'FAIL' END);
    END;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION pg_temp.expect_ok(p_sql TEXT, p_label TEXT)
RETURNS VOID AS $$
DECLARE
    v_state TEXT;
BEGIN
    BEGIN
        EXECUTE p_sql;
        INSERT INTO test_results(label, expected, actual, status)
        VALUES (p_label, 'SUCCESS', 'SUCCESS', 'PASS');
    EXCEPTION WHEN OTHERS THEN
        GET STACKED DIAGNOSTICS v_state = RETURNED_SQLSTATE;
        INSERT INTO test_results(label, expected, actual, status)
        VALUES (p_label, 'SUCCESS', v_state, 'FAIL');
    END;
END;
$$ LANGUAGE plpgsql;

DO $$
DECLARE
    v_suffix TEXT := txid_current()::TEXT;
    v_isbn TEXT := 'MT' || lpad(txid_current()::TEXT, 11, '0');
    v_pub_name TEXT := 'MANUAL-PUB-' || v_suffix;
    v_cat_name TEXT := 'MANUAL-CAT-' || v_suffix;
    v_auth_name TEXT := 'MANUAL-AUTH-' || v_suffix;
    v_book_title TEXT := 'MANUAL-BOOK-' || v_suffix;
    v_copy_code TEXT := 'MT-COPY-' || v_suffix;
    v_member_code TEXT := 'MT-MEM-' || v_suffix;
    v_member_email TEXT := 'manual.' || v_suffix || '@test.local';
    v_pub INT;
    v_cat INT;
    v_auth INT;
    v_book INT;
    v_copy INT;
    v_copy2 INT;
    v_member INT;
    v_loan INT;
    v_book2 INT;
    v_author2 INT;
    v_count BIGINT;
    v_orphans BIGINT;
    v_state TEXT;
BEGIN
    -- Setup: one complete valid relation chain.
    INSERT INTO publishers(name) VALUES (v_pub_name) RETURNING publisher_id INTO v_pub;
    INSERT INTO categories(name) VALUES (v_cat_name) RETURNING category_id INTO v_cat;
    INSERT INTO authors(name) VALUES (v_auth_name) RETURNING author_id INTO v_auth;
    INSERT INTO books(isbn, title, publisher_id, category_id, publication_year)
    VALUES (v_isbn, v_book_title, v_pub, v_cat, 2020) RETURNING book_id INTO v_book;
    INSERT INTO book_copies(book_id, copy_code, status)
    VALUES (v_book, v_copy_code, 'AVAILABLE') RETURNING copy_id INTO v_copy;
    INSERT INTO book_copies(book_id, copy_code, status)
    VALUES (v_book, v_copy_code || '-2', 'AVAILABLE') RETURNING copy_id INTO v_copy2;
    INSERT INTO members(member_code, full_name, email, status)
    VALUES (v_member_code, 'Manual Test Member', v_member_email, 'ACTIVE') RETURNING member_id INTO v_member;
    INSERT INTO book_authors(book_id, author_id) VALUES (v_book, v_auth);
    INSERT INTO loans(copy_id, member_id, loan_date, due_date, status)
    VALUES (v_copy, v_member, CURRENT_DATE, CURRENT_DATE + 14, 'ACTIVE') RETURNING loan_id INTO v_loan;

    -- PK tests: duplicate and null primary keys.
    PERFORM pg_temp.expect_error(
        format('INSERT INTO publishers(publisher_id,name) VALUES (%s,%L)', v_pub, 'DUP'),
        '23505', 'PK-01 publishers duplicate id');
    PERFORM pg_temp.expect_error(
        format('INSERT INTO categories(category_id,name) VALUES (%s,%L)', v_cat, 'DUP'),
        '23505', 'PK-02 categories duplicate id');
    PERFORM pg_temp.expect_error(
        format('INSERT INTO authors(author_id,name) VALUES (%s,%L)', v_auth, 'DUP'),
        '23505', 'PK-03 authors duplicate id');
    PERFORM pg_temp.expect_error(
        format('INSERT INTO books(book_id,isbn,title,publisher_id,category_id,publication_year) VALUES (%s,%L,%L,%s,%s,2020)',
               v_book, v_isbn || 'X', 'DUP', v_pub, v_cat),
        '23505', 'PK-04 books duplicate id');
    PERFORM pg_temp.expect_error(
        format('INSERT INTO book_copies(copy_id,book_id,copy_code) VALUES (%s,%s,%L)',
               v_copy, v_book, v_copy_code || '-DUP'),
        '23505', 'PK-05 book_copies duplicate id');
    PERFORM pg_temp.expect_error(
        format('INSERT INTO members(member_id,member_code,full_name,email) VALUES (%s,%L,%L,%L)',
               v_member, v_member_code || '-DUP', 'DUP', v_member_email || '.dup'),
        '23505', 'PK-06 members duplicate id');
    PERFORM pg_temp.expect_error(
        format('INSERT INTO loans(loan_id,copy_id,member_id,loan_date,due_date,status) VALUES (%s,%s,%s,CURRENT_DATE,CURRENT_DATE+14,%L)',
               v_loan, v_copy2, v_member, 'ACTIVE'),
        '23505', 'PK-07 loans duplicate id');
    PERFORM pg_temp.expect_error(
        format('INSERT INTO book_authors(book_id,author_id) VALUES (%s,%s)', v_book, v_auth),
        '23505', 'PK-08 book_authors duplicate composite');
    PERFORM pg_temp.expect_error(
        format('INSERT INTO book_authors(book_id,author_id) VALUES (NULL,%s)', v_auth),
        '23502', 'PK-09 book_authors null component');
    PERFORM pg_temp.expect_error(
        format('INSERT INTO books(book_id,isbn,title,publisher_id,category_id,publication_year) VALUES (NULL,%L,%L,%s,%s,2020)',
               v_isbn || 'N', 'NULL PK', v_pub, v_cat),
        '23502', 'PK-10 books explicit null id');

    -- Unique tests.
    PERFORM pg_temp.expect_error(
        format('INSERT INTO books(isbn,title,publisher_id,category_id,publication_year) VALUES (%L,%L,%s,%s,2020)',
               v_isbn, 'DUP ISBN', v_pub, v_cat),
        '23505', 'UNQ-01 books isbn');
    PERFORM pg_temp.expect_error(
        format('INSERT INTO book_copies(book_id,copy_code) VALUES (%s,%L)', v_book, v_copy_code),
        '23505', 'UNQ-02 book_copies copy_code');
    PERFORM pg_temp.expect_error(
        format('INSERT INTO members(member_code,full_name,email) VALUES (%L,%L,%L)',
               v_member_code, 'DUP CODE', v_member_email || '.code'),
        '23505', 'UNQ-03 members member_code');
    PERFORM pg_temp.expect_error(
        format('INSERT INTO members(member_code,full_name,email) VALUES (%L,%L,%L)',
               v_member_code || '-E', 'DUP EMAIL', upper(v_member_email)),
        '23505', 'UNQ-04 members email case-insensitive');
    PERFORM pg_temp.expect_error(
        format('INSERT INTO categories(name) VALUES (%L)', upper(v_cat_name)),
        '23505', 'UNQ-05 categories name case-insensitive');

    -- FK insert tests: every child table.
    PERFORM pg_temp.expect_error(
        format('INSERT INTO books(isbn,title,publisher_id,category_id,publication_year) VALUES (%L,%L,999999,%s,2020)',
               v_isbn || 'F1', 'FK PUB', v_cat),
        '23503', 'FK-01 books.publisher_id');
    PERFORM pg_temp.expect_error(
        format('INSERT INTO books(isbn,title,publisher_id,category_id,publication_year) VALUES (%L,%L,%s,999999,2020)',
               v_isbn || 'F2', 'FK CAT', v_pub),
        '23503', 'FK-02 books.category_id');
    PERFORM pg_temp.expect_error(
        format('INSERT INTO book_copies(book_id,copy_code) VALUES (999999,%L)', v_copy_code || '-FK'),
        '23503', 'FK-03 book_copies.book_id');
    PERFORM pg_temp.expect_error(
        format('INSERT INTO loans(copy_id,member_id,loan_date,due_date,status) VALUES (999999,%s,CURRENT_DATE,CURRENT_DATE+14,%L)',
               v_member, 'ACTIVE'),
        '23503', 'FK-04 loans.copy_id');
    PERFORM pg_temp.expect_error(
        format('INSERT INTO loans(copy_id,member_id,loan_date,due_date,status) VALUES (%s,999999,CURRENT_DATE,CURRENT_DATE+14,%L)',
               v_copy2, 'ACTIVE'),
        '23503', 'FK-05 loans.member_id');
    PERFORM pg_temp.expect_error(
        format('INSERT INTO book_authors(book_id,author_id) VALUES (999999,%s)', v_auth),
        '23503', 'FK-06 book_authors.book_id');
    PERFORM pg_temp.expect_error(
        format('INSERT INTO book_authors(book_id,author_id) VALUES (%s,999999)', v_book),
        '23503', 'FK-07 book_authors.author_id');

    -- FK update tests.
    PERFORM pg_temp.expect_error(
        format('UPDATE books SET publisher_id = 999999 WHERE book_id = %s', v_book),
        '23503', 'FK-08 update books.publisher_id');
    PERFORM pg_temp.expect_error(
        format('UPDATE loans SET member_id = 999999 WHERE loan_id = %s', v_loan),
        '23503', 'FK-09 update loans.member_id');

    -- ON DELETE RESTRICT tests.
    PERFORM pg_temp.expect_error(
        format('DELETE FROM publishers WHERE publisher_id = %s', v_pub),
        '23503', 'DEL-01 restrict publisher with books');
    PERFORM pg_temp.expect_error(
        format('DELETE FROM categories WHERE category_id = %s', v_cat),
        '23503', 'DEL-02 restrict category with books');
    PERFORM pg_temp.expect_error(
        format('DELETE FROM books WHERE book_id = %s', v_book),
        '23503', 'DEL-03 restrict book with copies');
    PERFORM pg_temp.expect_error(
        format('DELETE FROM book_copies WHERE copy_id = %s', v_copy),
        '23503', 'DEL-04 restrict copy with loans');
    PERFORM pg_temp.expect_error(
        format('DELETE FROM members WHERE member_id = %s', v_member),
        '23503', 'DEL-05 restrict member with loans');
    PERFORM pg_temp.expect_error(
        format('DELETE FROM authors WHERE author_id = %s', v_auth),
        '23503', 'DEL-06 restrict author with links');

    -- ON DELETE CASCADE: book with no copies, but with one author link.
    BEGIN
        INSERT INTO books(isbn, title, publisher_id, category_id, publication_year)
        VALUES (v_isbn || 'C', 'CASCADE BOOK', v_pub, v_cat, 2020) RETURNING book_id INTO v_book2;
        INSERT INTO authors(name) VALUES (v_auth_name || '-C') RETURNING author_id INTO v_author2;
        INSERT INTO book_authors(book_id, author_id) VALUES (v_book2, v_author2);
        DELETE FROM books WHERE book_id = v_book2;
        SELECT count(*) INTO v_count FROM book_authors WHERE book_id = v_book2;
        INSERT INTO test_results(label, expected, actual, status)
        VALUES ('DEL-07 cascade delete book succeeds', 'SUCCESS', 'SUCCESS', 'PASS');
        INSERT INTO test_results(label, expected, actual, status)
        VALUES ('DEL-08 cascade removes book_authors link', '0 links', v_count::TEXT || ' links',
                CASE WHEN v_count = 0 THEN 'PASS' ELSE 'FAIL' END);
    EXCEPTION WHEN OTHERS THEN
        GET STACKED DIAGNOSTICS v_state = RETURNED_SQLSTATE;
        INSERT INTO test_results(label, expected, actual, status)
        VALUES ('DEL-07 cascade delete book succeeds', 'SUCCESS', v_state, 'FAIL');
        INSERT INTO test_results(label, expected, actual, status)
        VALUES ('DEL-08 cascade removes book_authors link', '0 links', 'NOT_CHECKED', 'FAIL');
    END;

    -- Partial unique index: only one active loan per copy.
    PERFORM pg_temp.expect_error(
        format('INSERT INTO loans(copy_id,member_id,loan_date,due_date,status) VALUES (%s,%s,CURRENT_DATE,CURRENT_DATE+14,%L)',
               v_copy, v_member, 'ACTIVE'),
        '23505', 'LOAN-01 second active loan same copy');
    PERFORM pg_temp.expect_ok(
        format('UPDATE loans SET return_date = CURRENT_DATE, status = %L WHERE loan_id = %s', 'RETURNED', v_loan),
        'LOAN-02 return active loan');
    PERFORM pg_temp.expect_ok(
        format('INSERT INTO loans(copy_id,member_id,loan_date,due_date,status) VALUES (%s,%s,CURRENT_DATE,CURRENT_DATE+14,%L)',
               v_copy, v_member, 'ACTIVE'),
        'LOAN-03 new active loan after return');
    SELECT count(*) INTO v_count FROM loans WHERE copy_id = v_copy AND return_date IS NULL;
    INSERT INTO test_results(label, expected, actual, status)
    VALUES ('LOAN-04 exactly one active loan after re-borrow', '1', v_count::TEXT,
            CASE WHEN v_count = 1 THEN 'PASS' ELSE 'FAIL' END);

    -- CHECK constraints.
    PERFORM pg_temp.expect_error(
        format('INSERT INTO books(isbn,title,publisher_id,category_id,publication_year) VALUES (%L,%L,%s,%s,2020)',
               '123456789012', 'SHORT ISBN', v_pub, v_cat),
        '23514', 'CHK-01 isbn shorter than 13');
    PERFORM pg_temp.expect_error(
        format('INSERT INTO books(isbn,title,publisher_id,category_id,publication_year) VALUES (%L,%L,%s,%s,2020)',
               '123456789012345678901', 'LONG ISBN', v_pub, v_cat),
        '22001', 'CHK-02 isbn longer than varchar(20)');
    PERFORM pg_temp.expect_error(
        format('INSERT INTO books(isbn,title,publisher_id,category_id,publication_year) VALUES (%L,%L,%s,%s,999)',
               v_isbn || 'Y', 'BAD YEAR', v_pub, v_cat),
        '23514', 'CHK-03 publication_year out of range');
    PERFORM pg_temp.expect_error(
        format('INSERT INTO book_copies(book_id,copy_code,status) VALUES (%s,%L,%L)',
               v_book, v_copy_code || '-BAD', 'LOST'),
        '23514', 'CHK-04 invalid copy status');
    PERFORM pg_temp.expect_error(
        format('INSERT INTO members(member_code,full_name,email,status) VALUES (%L,%L,%L,%L)',
               v_member_code || '-S', 'BAD STATUS', 'status@test.local', 'BLOCKED'),
        '23514', 'CHK-05 invalid member status');
    PERFORM pg_temp.expect_error(
        format('INSERT INTO loans(copy_id,member_id,loan_date,due_date,status) VALUES (%s,%s,CURRENT_DATE,CURRENT_DATE+14,%L)',
               v_copy2, v_member, 'OVERDUE'),
        '23514', 'CHK-06 invalid loan status');
    PERFORM pg_temp.expect_error(
        format('INSERT INTO loans(copy_id,member_id,loan_date,due_date,status) VALUES (%s,%s,CURRENT_DATE,CURRENT_DATE+13,%L)',
               v_copy2, v_member, 'ACTIVE'),
        '23514', 'CHK-07 due date not exactly +14');
    PERFORM pg_temp.expect_error(
        format('INSERT INTO loans(copy_id,member_id,loan_date,due_date,return_date,status) VALUES (%s,%s,CURRENT_DATE,CURRENT_DATE+14,CURRENT_DATE-1,%L)',
               v_copy2, v_member, 'RETURNED'),
        '23514', 'CHK-08 return date before loan date');
    PERFORM pg_temp.expect_error(
        format('INSERT INTO loans(copy_id,member_id,loan_date,due_date,return_date,status) VALUES (%s,%s,CURRENT_DATE,CURRENT_DATE+14,CURRENT_DATE,%L)',
               v_copy2, v_member, 'ACTIVE'),
        '23514', 'CHK-09 ACTIVE loan with return_date');
    PERFORM pg_temp.expect_error(
        format('INSERT INTO loans(copy_id,member_id,loan_date,due_date,return_date,status) VALUES (%s,%s,CURRENT_DATE,CURRENT_DATE+14,NULL,%L)',
               v_copy2, v_member, 'RETURNED'),
        '23514', 'CHK-10 RETURNED loan without return_date');
    PERFORM pg_temp.expect_error(
        format('INSERT INTO members(member_code,full_name,email,status) VALUES (%L,%L,%L,%L)',
               v_member_code || '-B', 'BLANK EMAIL', '   ', 'ACTIVE'),
        '23514', 'CHK-11 blank email');

    -- Final orphan check after every probe.
    SELECT
        (SELECT count(*) FROM books b LEFT JOIN publishers p ON p.publisher_id = b.publisher_id WHERE p.publisher_id IS NULL)
      + (SELECT count(*) FROM books b LEFT JOIN categories c ON c.category_id = b.category_id WHERE c.category_id IS NULL)
      + (SELECT count(*) FROM book_copies bc LEFT JOIN books b ON b.book_id = bc.book_id WHERE b.book_id IS NULL)
      + (SELECT count(*) FROM loans l LEFT JOIN book_copies bc ON bc.copy_id = l.copy_id WHERE bc.copy_id IS NULL)
      + (SELECT count(*) FROM loans l LEFT JOIN members m ON m.member_id = l.member_id WHERE m.member_id IS NULL)
      + (SELECT count(*) FROM book_authors ba LEFT JOIN books b ON b.book_id = ba.book_id WHERE b.book_id IS NULL)
      + (SELECT count(*) FROM book_authors ba LEFT JOIN authors a ON a.author_id = ba.author_id WHERE a.author_id IS NULL)
      INTO v_orphans;
    INSERT INTO test_results(label, expected, actual, status)
    VALUES ('REL-01 no orphan rows after all probes', '0', v_orphans::TEXT,
            CASE WHEN v_orphans = 0 THEN 'PASS' ELSE 'FAIL' END);
END;
$$;

\echo '=== SUMMARY ==='
SELECT status, count(*) AS tests
FROM test_results
GROUP BY status
ORDER BY status;

\echo '=== DETAILS ==='
SELECT id, status, label, expected, actual
FROM test_results
ORDER BY id;

ROLLBACK;
