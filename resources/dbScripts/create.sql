CREATE TABLE sentiments (
  id serial NOT NULL,
  "name" character varying(25),
  CONSTRAINT sentiments_pkey PRIMARY KEY (id)
);

CREATE TABLE words
(
  id serial NOT NULL,
  concept_id integer,
  "name" character varying(100),
  sentiment_id smallint,
  from_input bool,
  CONSTRAINT words_pkey PRIMARY KEY (id),
  CONSTRAINT words_sentiment_id_fkey FOREIGN KEY (sentiment_id)
      REFERENCES sentiments (id) MATCH SIMPLE
      ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT words_id_sentiment_id_key UNIQUE (id, sentiment_id)
);

CREATE TABLE vertical_relations (
  id serial NOT NULL,
  words_id_higher integer,
  words_id_lower integer,
  CONSTRAINT vertical_relations_pkey PRIMARY KEY (id),
  CONSTRAINT vertical_relations_words_id_higher_fkey FOREIGN KEY (words_id_higher)
	REFERENCES words (id) MATCH SIMPLE
	ON UPDATE NO ACTION ON DELETE NO ACTION,
  CONSTRAINT vertical_relations_words_id_lower_fkey FOREIGN KEY (words_id_lower)
	REFERENCES words (id) MATCH SIMPLE
	ON UPDATE NO ACTION ON DELETE NO ACTION
)