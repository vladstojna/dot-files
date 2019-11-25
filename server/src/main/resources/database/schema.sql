--
-- PostgreSQL database dump
--

-- Dumped from database version 9.4.10
-- Dumped by pg_dump version 10.4

-- Started on 2019-11-25 09:57:38

SET statement_timeout = 0;
SET lock_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET client_min_messages = warning;

--
-- TOC entry 8 (class 2615 OID 6852750)
-- Name: dot-files; Type: SCHEMA; Schema: -; Owner: ist186428
--

CREATE SCHEMA "dot-files";


ALTER SCHEMA "dot-files" OWNER TO ist186428;

SET default_tablespace = '';

SET default_with_oids = false;

CREATE TYPE "dot-files".log_status AS ENUM (
	'Create',
    'Read',
    'Update Name',
    'Update Content',
    'Delete'
);
	
ALTER TYPE "dot-files".log_status OWNER TO ist186428;


--
-- TOC entry 212 (class 1259 OID 6854189)
-- Name: file; Type: TABLE; Schema: dot-files; Owner: ist186428
--

CREATE TABLE "dot-files".file (
    file_id SERIAL NOT NULL,
    filename VARCHAR(255) COLLATE pg_catalog."en_US.utf8" NOT NULL,
    local_path VARCHAR(255) COLLATE pg_catalog."en_US.utf8" NOT NULL,
    shared BOOLEAN NOT NULL,
    PRIMARY KEY (file_id)
);


ALTER TABLE "dot-files".file OWNER TO ist186428;

--
-- TOC entry 214 (class 1259 OID 6854341)
-- Name: file_in_transition; Type: TABLE; Schema: dot-files; Owner: ist186428
--

CREATE TABLE "dot-files".file_in_transition (
    username_send VARCHAR(255) COLLATE pg_catalog."en_US.utf8" NOT NULL,
    file_id SERIAL NOT NULL,
    username_receive VARCHAR(255) COLLATE pg_catalog."en_US.utf8" NOT NULL,
    shared_key VARCHAR(255) COLLATE pg_catalog."en_US.utf8" NOT NULL,
    PRIMARY KEY (username_send, file_id, username_receive),
    CONSTRAINT "FK_file_in_transition_shared_file" FOREIGN KEY (username_send, file_id) REFERENCES "dot-files".user_file(username, file_id),
    CONSTRAINT "FK_file_in_transition_r3ds_user" FOREIGN KEY (username_receive) REFERENCES "dot-files".r3ds_user(username)
);


ALTER TABLE "dot-files".file_in_transition OWNER TO ist186428;

--
-- TOC entry 215 (class 1259 OID 6856811)
-- Name: log_file; Type: TABLE; Schema: dot-files; Owner: ist186428
--

CREATE TABLE "dot-files".log_file (
    file_id SERIAL NOT NULL,
    date_time TIMESTAMP without time zone NOT NULL,
    username VARCHAR(255) COLLATE pg_catalog."en_US.utf8" NOT NULL,
    status "dot-files".log_status NOT NULL,
    PRIMARY KEY (file_id, date_time),
    CONSTRAINT "FK_log_file_file" FOREIGN KEY (file_id) REFERENCES "dot-files".file(file_id)
);


ALTER TABLE "dot-files".log_file OWNER TO ist186428;

--
-- TOC entry 211 (class 1259 OID 6853341)
-- Name: r3ds_user; Type: TABLE; Schema: dot-files; Owner: ist186428
--

CREATE TABLE "dot-files".r3ds_user (
    username VARCHAR(255) COLLATE pg_catalog."en_US.utf8" NOT NULL,
    password VARCHAR(255) COLLATE pg_catalog."en_US.utf8" NOT NULL,
    PRIMARY KEY (username)
);


ALTER TABLE "dot-files".r3ds_user OWNER TO ist186428;

--
-- TOC entry 213 (class 1259 OID 6854212)
-- Name: user_file; Type: TABLE; Schema: dot-files; Owner: ist186428
--

CREATE TABLE "dot-files".user_file (
    username VARCHAR(255) COLLATE pg_catalog."en_US.utf8" NOT NULL,
    file_id SERIAL NOT NULL,
    shared_key VARCHAR(255) COLLATE pg_catalog."en_US.utf8",
    PRIMARY KEY (username, file_id),
    CONSTRAINT "FK_shared_file_user" FOREIGN KEY (username) REFERENCES "dot-files".r3ds_user(username),
    CONSTRAINT "FK_shared_file_file" FOREIGN KEY (file_id) REFERENCES "dot-files".file(file_id)
);


ALTER TABLE "dot-files".user_file OWNER TO ist186428;


--
-- TOC entry 2027 (class 1259 OID 6857498)
-- Name: file_filename_idx; Type: INDEX; Schema: dot-files; Owner: ist186428
--

CREATE INDEX file_filename_idx ON "dot-files".file USING btree (filename);


--
-- TOC entry 2033 (class 1259 OID 6857497)
-- Name: file_in_transition_username_receive_idx; Type: INDEX; Schema: dot-files; Owner: ist186428
--

CREATE INDEX file_in_transition_username_receive_idx ON "dot-files".file_in_transition USING btree (username_receive);


--
-- TOC entry 2030 (class 1259 OID 6857084)
-- Name: user_file_file_id_idx; Type: INDEX; Schema: dot-files; Owner: ist186428
--

CREATE INDEX user_file_file_id_idx ON "dot-files".user_file USING btree (file_id);


-- Completed on 2019-11-25 09:57:47

--
-- PostgreSQL database dump complete
--

