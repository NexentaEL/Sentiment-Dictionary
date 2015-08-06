DROP TABLE IF EXISTS entry_rel;
DROP TABLE IF EXISTS text_entries;
DROP TABLE IF EXISTS vertical_relations;
DROP TABLE IF EXISTS concepts;
DROP TABLE IF EXISTS words;
DROP TABLE IF EXISTS sentiments;

CREATE TABLE sentiments (
  id serial NOT NULL,
  "name" character varying(25) NOT NULL,
  CONSTRAINT sentiments_pkey PRIMARY KEY (id)
);

CREATE TABLE words
(
  id integer NOT NULL,
  word character varying(200) NOT NULL,
  sentiment_id smallint NOT NULL,
  CONSTRAINT words_pkey PRIMARY KEY (id),
  CONSTRAINT words_sentiment_id_fkey FOREIGN KEY (sentiment_id)
      REFERENCES sentiments (id) MATCH SIMPLE
      ON UPDATE CASCADE ON DELETE CASCADE
);

CREATE TABLE concepts
(
  id serial NOT NULL,
  concept_id integer NOT NULL,
  "name" character varying(200),
  word_id integer,
  sentiment_id smallint,
  CONSTRAINT concepts_pkey PRIMARY KEY (id),
  CONSTRAINT concepts_word_id_fkey FOREIGN KEY (word_id)
      REFERENCES words (id) MATCH SIMPLE
      ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT concepts_sentiment_id_fkey FOREIGN KEY (sentiment_id)
      REFERENCES sentiments (id) MATCH SIMPLE
      ON UPDATE CASCADE ON DELETE CASCADE
);

CREATE TABLE vertical_relations (
  id serial NOT NULL,
  concept_id_higher integer NOT NULL,
  concept_id_lower integer NOT NULL,
  CONSTRAINT vertical_relations_pkey PRIMARY KEY (id),
  CONSTRAINT vertical_relations_words_id_higher_fkey FOREIGN KEY (concept_id_higher)
	    REFERENCES concepts (id) MATCH SIMPLE
	    ON UPDATE NO ACTION ON DELETE NO ACTION,
  CONSTRAINT vertical_relations_words_id_lower_fkey FOREIGN KEY (concept_id_lower)
	    REFERENCES concepts (id) MATCH SIMPLE
	    ON UPDATE NO ACTION ON DELETE NO ACTION
);

CREATE TABLE text_entries (
  id serial NOT NULL,
  entry_id integer NOT NULL,
  "name" character varying(200),
  word_id integer,
  sentiment_id smallint NOT NULL,
  CONSTRAINT text_entries_pkey PRIMARY KEY (id),
  CONSTRAINT text_entries_word_id_fkey FOREIGN KEY (word_id)
      REFERENCES words (id) MATCH SIMPLE
      ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT text_entries_sentiment_id_fkey FOREIGN KEY (sentiment_id)
      REFERENCES sentiments (id) MATCH SIMPLE
      ON UPDATE CASCADE ON DELETE CASCADE
);

CREATE TABLE entry_rel (
  id serial NOT NULL,
  text_entry_id  integer NOT NULL,
  concept_id  integer NOT NULL,
  CONSTRAINT text_entry_id_fkey FOREIGN KEY (text_entry_id)
      REFERENCES text_entries (id) MATCH SIMPLE
      ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT concept_id_fkey FOREIGN KEY (concept_id)
      REFERENCES concepts (id) MATCH SIMPLE
      ON UPDATE CASCADE ON DELETE CASCADE
)