create table conversation_holder
(
    id                 varchar(50) primary key,
    expires_at         datetime,
    conversation_class varchar(500),
    conversation_value varchar(8000)
);

-- optional
create index idx_conversation_holder_expires_at on conversation_holder (expires_at);
