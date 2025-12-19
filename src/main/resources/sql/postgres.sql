create table conversation_holder
(
    id                 varchar(50) primary key,
    expires_at         timestamp,
    conversation_class varchar(500),
    conversation_value text
);

-- optional
create index idx_conversation_holder_expires_at on conversation_holder (expires_at);
