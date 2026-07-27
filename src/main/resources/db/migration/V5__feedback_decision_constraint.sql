DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'recommendation_decision_allowed'
    ) THEN
        ALTER TABLE v2_recommendation_feedback
            ADD CONSTRAINT recommendation_decision_allowed CHECK (decision IN ('ACCEPTED','REJECTED'));
    END IF;
END $$;
