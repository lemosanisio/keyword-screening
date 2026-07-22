ALTER TABLE integration_outbox
    DROP CONSTRAINT ux_integration_outbox_aggregate;

ALTER TABLE integration_outbox
    ADD COLUMN logical_id VARCHAR(100);

UPDATE integration_outbox
SET logical_id = COALESCE(
    envelope -> 'payload' ->> 'signalId',
    envelope -> 'payload' ->> 'reviewRequestId',
    envelope -> 'payload' ->> 'evaluationId',
    aggregate_id
);

ALTER TABLE integration_outbox
    ALTER COLUMN logical_id SET NOT NULL;

ALTER TABLE integration_outbox
    ADD CONSTRAINT ux_integration_outbox_logical UNIQUE (event_type, event_version, logical_id);

CREATE UNIQUE INDEX ux_integration_outbox_evaluation_completed_v2
    ON integration_outbox (aggregate_id)
    WHERE event_type = 'TransactionEvaluationCompleted' AND event_version = 2;

CREATE UNIQUE INDEX ux_integration_outbox_manual_review_v2
    ON integration_outbox (aggregate_id)
    WHERE event_type = 'ManualReviewRequested' AND event_version = 2;
