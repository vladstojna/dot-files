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

--
-- TOC entry 8 (class 2615 OID 6894080)
-- Name: dot-files; Type: SCHEMA; Schema: -; Owner: ist186428
--

CREATE SCHEMA "dot-files";


ALTER SCHEMA "dot-files" OWNER TO ist186428;

--
-- TOC entry 636 (class 1247 OID 7552952)
-- Name: log_status; Type: TYPE; Schema: dot-files; Owner: ist186428
--

CREATE TYPE "dot-files".log_status AS ENUM (
    'Create',
    'Read',
    'Update Name',
    'Update Content',
    'Shared By',
    'Shared With',
    'Delete',
    'Unshare'
);


ALTER TYPE "dot-files".log_status OWNER TO ist186428;

SET default_tablespace = '';

SET default_with_oids = false;

--
-- TOC entry 213 (class 1259 OID 6894091)
-- Name: file; Type: TABLE; Schema: dot-files; Owner: ist186428
--

CREATE TABLE "dot-files".file (
    file_id integer NOT NULL,
    filename character varying(255) COLLATE pg_catalog."en_US.utf8" NOT NULL,
    shared boolean NOT NULL,
    owner_username character varying(255),
    local_path character varying(255)
);


ALTER TABLE "dot-files".file OWNER TO ist186428;

--
-- TOC entry 212 (class 1259 OID 6894089)
-- Name: file_file_id_seq; Type: SEQUENCE; Schema: dot-files; Owner: ist186428
--

CREATE SEQUENCE "dot-files".file_file_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE "dot-files".file_file_id_seq OWNER TO ist186428;

--
-- TOC entry 2184 (class 0 OID 0)
-- Dependencies: 212
-- Name: file_file_id_seq; Type: SEQUENCE OWNED BY; Schema: dot-files; Owner: ist186428
--

ALTER SEQUENCE "dot-files".file_file_id_seq OWNED BY "dot-files".file.file_id;


--
-- TOC entry 217 (class 1259 OID 6894123)
-- Name: file_in_transition; Type: TABLE; Schema: dot-files; Owner: ist186428
--

CREATE TABLE "dot-files".file_in_transition (
    username_send character varying(255) COLLATE pg_catalog."en_US.utf8" NOT NULL,
    file_id integer NOT NULL,
    username_receive character varying(255) COLLATE pg_catalog."en_US.utf8" NOT NULL,
    shared_key bytea
);


ALTER TABLE "dot-files".file_in_transition OWNER TO ist186428;

--
-- TOC entry 216 (class 1259 OID 6894121)
-- Name: file_in_transition_file_id_seq; Type: SEQUENCE; Schema: dot-files; Owner: ist186428
--

CREATE SEQUENCE "dot-files".file_in_transition_file_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE "dot-files".file_in_transition_file_id_seq OWNER TO ist186428;

--
-- TOC entry 2185 (class 0 OID 0)
-- Dependencies: 216
-- Name: file_in_transition_file_id_seq; Type: SEQUENCE OWNED BY; Schema: dot-files; Owner: ist186428
--

ALTER SEQUENCE "dot-files".file_in_transition_file_id_seq OWNED BY "dot-files".file_in_transition.file_id;


--
-- TOC entry 219 (class 1259 OID 6894144)
-- Name: log_file; Type: TABLE; Schema: dot-files; Owner: ist186428
--

CREATE TABLE "dot-files".log_file (
    file_id integer NOT NULL,
    date_time timestamp without time zone NOT NULL,
    username character varying(255) COLLATE pg_catalog."en_US.utf8" NOT NULL,
    status "dot-files".log_status
);


ALTER TABLE "dot-files".log_file OWNER TO ist186428;

--
-- TOC entry 218 (class 1259 OID 6894142)
-- Name: log_file_file_id_seq; Type: SEQUENCE; Schema: dot-files; Owner: ist186428
--

CREATE SEQUENCE "dot-files".log_file_file_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE "dot-files".log_file_file_id_seq OWNER TO ist186428;

--
-- TOC entry 2186 (class 0 OID 0)
-- Dependencies: 218
-- Name: log_file_file_id_seq; Type: SEQUENCE OWNED BY; Schema: dot-files; Owner: ist186428
--

ALTER SEQUENCE "dot-files".log_file_file_id_seq OWNED BY "dot-files".log_file.file_id;


--
-- TOC entry 211 (class 1259 OID 6894081)
-- Name: r3ds_user; Type: TABLE; Schema: dot-files; Owner: ist186428
--

CREATE TABLE "dot-files".r3ds_user (
    username character varying(255) COLLATE pg_catalog."en_US.utf8" NOT NULL,
    password character varying(255) COLLATE pg_catalog."en_US.utf8" NOT NULL
);


ALTER TABLE "dot-files".r3ds_user OWNER TO ist186428;

--
-- TOC entry 215 (class 1259 OID 6894102)
-- Name: user_file; Type: TABLE; Schema: dot-files; Owner: ist186428
--

CREATE TABLE "dot-files".user_file (
    username character varying(255) COLLATE pg_catalog."en_US.utf8" NOT NULL,
    file_id integer NOT NULL,
    shared_key bytea
);


ALTER TABLE "dot-files".user_file OWNER TO ist186428;

--
-- TOC entry 214 (class 1259 OID 6894100)
-- Name: user_file_file_id_seq; Type: SEQUENCE; Schema: dot-files; Owner: ist186428
--

CREATE SEQUENCE "dot-files".user_file_file_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE "dot-files".user_file_file_id_seq OWNER TO ist186428;

--
-- TOC entry 2187 (class 0 OID 0)
-- Dependencies: 214
-- Name: user_file_file_id_seq; Type: SEQUENCE OWNED BY; Schema: dot-files; Owner: ist186428
--

ALTER SEQUENCE "dot-files".user_file_file_id_seq OWNED BY "dot-files".user_file.file_id;


--
-- TOC entry 2032 (class 2604 OID 6894094)
-- Name: file file_id; Type: DEFAULT; Schema: dot-files; Owner: ist186428
--

ALTER TABLE ONLY "dot-files".file ALTER COLUMN file_id SET DEFAULT nextval('"dot-files".file_file_id_seq'::regclass);


--
-- TOC entry 2034 (class 2604 OID 6894126)
-- Name: file_in_transition file_id; Type: DEFAULT; Schema: dot-files; Owner: ist186428
--

ALTER TABLE ONLY "dot-files".file_in_transition ALTER COLUMN file_id SET DEFAULT nextval('"dot-files".file_in_transition_file_id_seq'::regclass);


--
-- TOC entry 2035 (class 2604 OID 6894147)
-- Name: log_file file_id; Type: DEFAULT; Schema: dot-files; Owner: ist186428
--

ALTER TABLE ONLY "dot-files".log_file ALTER COLUMN file_id SET DEFAULT nextval('"dot-files".log_file_file_id_seq'::regclass);


--
-- TOC entry 2033 (class 2604 OID 6894105)
-- Name: user_file file_id; Type: DEFAULT; Schema: dot-files; Owner: ist186428
--

ALTER TABLE ONLY "dot-files".user_file ALTER COLUMN file_id SET DEFAULT nextval('"dot-files".user_file_file_id_seq'::regclass);


--
-- TOC entry 2172 (class 0 OID 6894091)
-- Dependencies: 213
-- Data for Name: file; Type: TABLE DATA; Schema: dot-files; Owner: ist186428
--

COPY "dot-files".file (file_id, filename, shared, owner_username, local_path) FROM stdin;
18	relatorio3.txt	f	costa	C:\\Users\\Costa\\.r3ds\\backup\\costa\\relatorio3.txt
\.


--
-- TOC entry 2176 (class 0 OID 6894123)
-- Dependencies: 217
-- Data for Name: file_in_transition; Type: TABLE DATA; Schema: dot-files; Owner: ist186428
--

COPY "dot-files".file_in_transition (username_send, file_id, username_receive, shared_key) FROM stdin;
\.


--
-- TOC entry 2178 (class 0 OID 6894144)
-- Dependencies: 219
-- Data for Name: log_file; Type: TABLE DATA; Schema: dot-files; Owner: ist186428
--

COPY "dot-files".log_file (file_id, date_time, username, status) FROM stdin;
18	2019-12-11 15:47:24.883	costa	Create
18	2019-12-11 15:47:26.386	costa	Update Content
18	2019-12-11 15:47:53.39	costa	Read
18	2019-12-11 15:56:26.812	costa	Read
18	2019-12-11 16:00:27.32	costa	Read
18	2019-12-11 16:01:07.419	costa	Read
\.


--
-- TOC entry 2170 (class 0 OID 6894081)
-- Dependencies: 211
-- Data for Name: r3ds_user; Type: TABLE DATA; Schema: dot-files; Owner: ist186428
--

COPY "dot-files".r3ds_user (username, password) FROM stdin;
costa	$2a$10$sEKGQwPkragrR4OWXIsL4.rcCANROVcrpICOHXnGhs9JLsvGnXJCm
teste3	$2a$10$n5tEdOUFfpPpDk7ice9X2ulS4BUzuTXrYnbq/S48d6ngU2YrpRH4O
\.


--
-- TOC entry 2174 (class 0 OID 6894102)
-- Dependencies: 215
-- Data for Name: user_file; Type: TABLE DATA; Schema: dot-files; Owner: ist186428
--

COPY "dot-files".user_file (username, file_id, shared_key) FROM stdin;
costa	18	\N
\.


--
-- TOC entry 2188 (class 0 OID 0)
-- Dependencies: 212
-- Name: file_file_id_seq; Type: SEQUENCE SET; Schema: dot-files; Owner: ist186428
--

SELECT pg_catalog.setval('"dot-files".file_file_id_seq', 18, true);


--
-- TOC entry 2189 (class 0 OID 0)
-- Dependencies: 216
-- Name: file_in_transition_file_id_seq; Type: SEQUENCE SET; Schema: dot-files; Owner: ist186428
--

SELECT pg_catalog.setval('"dot-files".file_in_transition_file_id_seq', 1, false);


--
-- TOC entry 2190 (class 0 OID 0)
-- Dependencies: 218
-- Name: log_file_file_id_seq; Type: SEQUENCE SET; Schema: dot-files; Owner: ist186428
--

SELECT pg_catalog.setval('"dot-files".log_file_file_id_seq', 1, false);


--
-- TOC entry 2191 (class 0 OID 0)
-- Dependencies: 214
-- Name: user_file_file_id_seq; Type: SEQUENCE SET; Schema: dot-files; Owner: ist186428
--

SELECT pg_catalog.setval('"dot-files".user_file_file_id_seq', 1, false);


--
-- TOC entry 2041 (class 2606 OID 7708516)
-- Name: file file_filename_owner_username_key; Type: CONSTRAINT; Schema: dot-files; Owner: ist186428
--

ALTER TABLE ONLY "dot-files".file
    ADD CONSTRAINT file_filename_owner_username_key UNIQUE (filename, owner_username);


--
-- TOC entry 2050 (class 2606 OID 6894131)
-- Name: file_in_transition file_in_transition_pkey; Type: CONSTRAINT; Schema: dot-files; Owner: ist186428
--

ALTER TABLE ONLY "dot-files".file_in_transition
    ADD CONSTRAINT file_in_transition_pkey PRIMARY KEY (username_send, file_id, username_receive);


--
-- TOC entry 2043 (class 2606 OID 7557003)
-- Name: file file_local_path_key; Type: CONSTRAINT; Schema: dot-files; Owner: ist186428
--

ALTER TABLE ONLY "dot-files".file
    ADD CONSTRAINT file_local_path_key UNIQUE (local_path);


--
-- TOC entry 2045 (class 2606 OID 6894099)
-- Name: file file_pkey; Type: CONSTRAINT; Schema: dot-files; Owner: ist186428
--

ALTER TABLE ONLY "dot-files".file
    ADD CONSTRAINT file_pkey PRIMARY KEY (file_id);


--
-- TOC entry 2054 (class 2606 OID 6894149)
-- Name: log_file log_file_pkey; Type: CONSTRAINT; Schema: dot-files; Owner: ist186428
--

ALTER TABLE ONLY "dot-files".log_file
    ADD CONSTRAINT log_file_pkey PRIMARY KEY (file_id, date_time);


--
-- TOC entry 2037 (class 2606 OID 6894088)
-- Name: r3ds_user r3ds_user_pkey; Type: CONSTRAINT; Schema: dot-files; Owner: ist186428
--

ALTER TABLE ONLY "dot-files".r3ds_user
    ADD CONSTRAINT r3ds_user_pkey PRIMARY KEY (username);


--
-- TOC entry 2048 (class 2606 OID 6894110)
-- Name: user_file user_file_pkey; Type: CONSTRAINT; Schema: dot-files; Owner: ist186428
--

ALTER TABLE ONLY "dot-files".user_file
    ADD CONSTRAINT user_file_pkey PRIMARY KEY (username, file_id);


--
-- TOC entry 2038 (class 1259 OID 7550194)
-- Name: FKI_file_r3ds_user; Type: INDEX; Schema: dot-files; Owner: ist186428
--

CREATE INDEX "FKI_file_r3ds_user" ON "dot-files".file USING btree (owner_username);


--
-- TOC entry 2052 (class 1259 OID 7670457)
-- Name: FKI_log_file_user_file; Type: INDEX; Schema: dot-files; Owner: ist186428
--

CREATE INDEX "FKI_log_file_user_file" ON "dot-files".log_file USING btree (username, file_id);


--
-- TOC entry 2039 (class 1259 OID 6894160)
-- Name: file_filename_idx; Type: INDEX; Schema: dot-files; Owner: ist186428
--

CREATE INDEX file_filename_idx ON "dot-files".file USING btree (filename);


--
-- TOC entry 2051 (class 1259 OID 6894161)
-- Name: file_in_transition_username_receive_idx; Type: INDEX; Schema: dot-files; Owner: ist186428
--

CREATE INDEX file_in_transition_username_receive_idx ON "dot-files".file_in_transition USING btree (username_receive);


--
-- TOC entry 2046 (class 1259 OID 6894162)
-- Name: user_file_file_id_idx; Type: INDEX; Schema: dot-files; Owner: ist186428
--

CREATE INDEX user_file_file_id_idx ON "dot-files".user_file USING btree (file_id);


--
-- TOC entry 2059 (class 2606 OID 6894137)
-- Name: file_in_transition FK_file_in_transition_r3ds_user; Type: FK CONSTRAINT; Schema: dot-files; Owner: ist186428
--

ALTER TABLE ONLY "dot-files".file_in_transition
    ADD CONSTRAINT "FK_file_in_transition_r3ds_user" FOREIGN KEY (username_receive) REFERENCES "dot-files".r3ds_user(username);


--
-- TOC entry 2058 (class 2606 OID 6894132)
-- Name: file_in_transition FK_file_in_transition_shared_file; Type: FK CONSTRAINT; Schema: dot-files; Owner: ist186428
--

ALTER TABLE ONLY "dot-files".file_in_transition
    ADD CONSTRAINT "FK_file_in_transition_shared_file" FOREIGN KEY (username_send, file_id) REFERENCES "dot-files".user_file(username, file_id);


--
-- TOC entry 2055 (class 2606 OID 7550189)
-- Name: file FK_file_r3ds_user; Type: FK CONSTRAINT; Schema: dot-files; Owner: ist186428
--

ALTER TABLE ONLY "dot-files".file
    ADD CONSTRAINT "FK_file_r3ds_user" FOREIGN KEY (owner_username) REFERENCES "dot-files".r3ds_user(username);


--
-- TOC entry 2060 (class 2606 OID 7670452)
-- Name: log_file FK_log_file_user_file; Type: FK CONSTRAINT; Schema: dot-files; Owner: ist186428
--

ALTER TABLE ONLY "dot-files".log_file
    ADD CONSTRAINT "FK_log_file_user_file" FOREIGN KEY (username, file_id) REFERENCES "dot-files".user_file(username, file_id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- TOC entry 2057 (class 2606 OID 6894116)
-- Name: user_file FK_shared_file_file; Type: FK CONSTRAINT; Schema: dot-files; Owner: ist186428
--

ALTER TABLE ONLY "dot-files".user_file
    ADD CONSTRAINT "FK_shared_file_file" FOREIGN KEY (file_id) REFERENCES "dot-files".file(file_id);


--
-- TOC entry 2056 (class 2606 OID 6894111)
-- Name: user_file FK_shared_file_user; Type: FK CONSTRAINT; Schema: dot-files; Owner: ist186428
--

ALTER TABLE ONLY "dot-files".user_file
    ADD CONSTRAINT "FK_shared_file_user" FOREIGN KEY (username) REFERENCES "dot-files".r3ds_user(username);


-- Completed on 2019-12-11 16:09:54

--
-- PostgreSQL database dump complete
--

