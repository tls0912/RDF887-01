-- GripperRequest lifecycle optimization indexes.
-- Apply manually after checking existing indexes in the target database.
--
-- Existing DDL already has:
--   gripper_request(gripper_id, accepted, created_time)
--
-- This index matches GripperTaskMapper.findTopTaskByGripperOrdered:
--   WHERE gripper_id = ?
--     AND task_status IN (...)
--     AND done_time IS NULL
--   ORDER BY CASE task_status ..., priority_level DESC, id ASC

CREATE INDEX idx_gripper_task_monitor_pick
    ON gripper_task (gripper_id, done_time, task_status, priority_level, id);
