CREATE TABLE news_brief (
    article_id UUID NOT NULL,
    input_hash UUID NOT NULL,
    headline TEXT NOT NULL,
    summary TEXT NOT NULL,
    commentary TEXT NOT NULL,
    PRIMARY KEY (article_id, input_hash)
);
