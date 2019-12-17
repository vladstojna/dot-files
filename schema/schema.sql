--
-- PostgreSQL database dump
--

-- Dumped from database version 9.4.10
-- Dumped by pg_dump version 10.4

-- Started on 2019-12-11 16:09:52

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET client_min_messages = warning;
SET row_security = off;


CREATE SCHEMA r3ds_schema;


CREATE TYPE r3ds_schema.log_status AS ENUM (
    'Create',
    'Read',
    'Update Name',
    'Update Content',
    'Shared By',
    'Shared With',
    'Delete',
    'Unshare'
);

SET default_tablespace = '';

SET default_with_oids = false;


CREATE TABLE r3ds_schema.file (
    file_id integer NOT NULL,
    filename character varying(255) COLLATE pg_catalog."en_US.utf8" NOT NULL,
    shared boolean NOT NULL,
    owner_username character varying(255),
    local_path character varying(255)
);


CREATE SEQUENCE r3ds_schema.file_file_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE r3ds_schema.file_file_id_seq OWNED BY r3ds_schema.file.file_id;


CREATE TABLE r3ds_schema.file_in_transition (
    username_send character varying(255) COLLATE pg_catalog."en_US.utf8" NOT NULL,
    file_id integer NOT NULL,
    username_receive character varying(255) COLLATE pg_catalog."en_US.utf8" NOT NULL,
    shared_key bytea
);


CREATE SEQUENCE r3ds_schema.file_in_transition_file_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE r3ds_schema.file_in_transition_file_id_seq OWNED BY r3ds_schema.file_in_transition.file_id;


CREATE TABLE r3ds_schema.log_file (
    file_id integer NOT NULL,
    date_time timestamp without time zone NOT NULL,
    username character varying(255) COLLATE pg_catalog."en_US.utf8" NOT NULL,
    status r3ds_schema.log_status
);


CREATE SEQUENCE r3ds_schema.log_file_file_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE r3ds_schema.log_file_file_id_seq OWNED BY r3ds_schema.log_file.file_id;


CREATE TABLE r3ds_schema.r3ds_user (
    username character varying(255) COLLATE pg_catalog."en_US.utf8" NOT NULL,
    password character varying(255) COLLATE pg_catalog."en_US.utf8" NOT NULL
);

CREATE TABLE r3ds_schema.user_file (
    username character varying(255) COLLATE pg_catalog."en_US.utf8" NOT NULL,
    file_id integer NOT NULL,
    shared_key bytea
);


CREATE SEQUENCE r3ds_schema.user_file_file_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE r3ds_schema.user_file_file_id_seq OWNED BY r3ds_schema.user_file.file_id;

ALTER TABLE ONLY r3ds_schema.file ALTER COLUMN file_id SET DEFAULT nextval('r3ds_schema.file_file_id_seq'::regclass);

ALTER TABLE ONLY r3ds_schema.file_in_transition ALTER COLUMN file_id SET DEFAULT nextval('r3ds_schema.file_in_transition_file_id_seq'::regclass);

ALTER TABLE ONLY r3ds_schema.log_file ALTER COLUMN file_id SET DEFAULT nextval('r3ds_schema.log_file_file_id_seq'::regclass);

ALTER TABLE ONLY r3ds_schema.user_file ALTER COLUMN file_id SET DEFAULT nextval('r3ds_schema.user_file_file_id_seq'::regclass);


SELECT pg_catalog.setval('r3ds_schema.file_file_id_seq', 18, true);

SELECT pg_catalog.setval('r3ds_schema.file_in_transition_file_id_seq', 1, false);

SELECT pg_catalog.setval('r3ds_schema.log_file_file_id_seq', 1, false);

SELECT pg_catalog.setval('r3ds_schema.user_file_file_id_seq', 1, false);


ALTER TABLE ONLY r3ds_schema.file
    ADD CONSTRAINT file_filename_owner_username_key UNIQUE (filename, owner_username);

ALTER TABLE ONLY r3ds_schema.file_in_transition
    ADD CONSTRAINT file_in_transition_pkey PRIMARY KEY (username_send, file_id, username_receive);

ALTER TABLE ONLY r3ds_schema.file
    ADD CONSTRAINT file_local_path_key UNIQUE (local_path);

ALTER TABLE ONLY r3ds_schema.file
    ADD CONSTRAINT file_pkey PRIMARY KEY (file_id);

ALTER TABLE ONLY r3ds_schema.log_file
    ADD CONSTRAINT log_file_pkey PRIMARY KEY (file_id, date_time);

ALTER TABLE ONLY r3ds_schema.r3ds_user
    ADD CONSTRAINT r3ds_user_pkey PRIMARY KEY (username);

ALTER TABLE ONLY r3ds_schema.user_file
    ADD CONSTRAINT user_file_pkey PRIMARY KEY (username, file_id);


CREATE INDEX "FKI_file_r3ds_user" ON r3ds_schema.file USING btree (owner_username);

CREATE INDEX "FKI_log_file_user_file" ON r3ds_schema.log_file USING btree (username, file_id);

CREATE INDEX file_filename_idx ON r3ds_schema.file USING btree (filename);

CREATE INDEX file_in_transition_username_receive_idx ON r3ds_schema.file_in_transition USING btree (username_receive);

CREATE INDEX user_file_file_id_idx ON r3ds_schema.user_file USING btree (file_id);


ALTER TABLE ONLY r3ds_schema.file_in_transition
    ADD CONSTRAINT "FK_file_in_transition_r3ds_user" FOREIGN KEY (username_receive) REFERENCES r3ds_schema.r3ds_user(username);

ALTER TABLE ONLY r3ds_schema.file_in_transition
    ADD CONSTRAINT "FK_file_in_transition_shared_file" FOREIGN KEY (username_send, file_id) REFERENCES r3ds_schema.user_file(username, file_id);

ALTER TABLE ONLY r3ds_schema.file
    ADD CONSTRAINT "FK_file_r3ds_user" FOREIGN KEY (owner_username) REFERENCES r3ds_schema.r3ds_user(username);

ALTER TABLE ONLY r3ds_schema.log_file
    ADD CONSTRAINT "FK_log_file_user_file" FOREIGN KEY (username, file_id) REFERENCES r3ds_schema.user_file(username, file_id) ON UPDATE CASCADE ON DELETE CASCADE;

ALTER TABLE ONLY r3ds_schema.user_file
    ADD CONSTRAINT "FK_shared_file_file" FOREIGN KEY (file_id) REFERENCES r3ds_schema.file(file_id);

ALTER TABLE ONLY r3ds_schema.user_file
    ADD CONSTRAINT "FK_shared_file_user" FOREIGN KEY (username) REFERENCES r3ds_schema.r3ds_user(username);

-- Completed on 2019-12-11 16:09:54

--
-- PostgreSQL database dump complete
--

