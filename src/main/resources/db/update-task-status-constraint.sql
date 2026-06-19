-- ============================================================
-- Migration: update CHECK constraint on task.status
-- Thêm SUBMITTED, REVISE; xoá REJECTED khỏi allowed values.
-- Chạy sau khi TaskStatus.java đã được cập nhật.
-- ============================================================

-- 1. Drop CHECK constraint cũ (nếu có)
DECLARE @sql NVARCHAR(MAX) = '';
SELECT @sql = 'ALTER TABLE task DROP CONSTRAINT ' + QUOTENAME(c.name) + ';'
FROM sys.columns col
JOIN sys.check_constraints c ON c.parent_object_id = col.object_id AND c.parent_column_id = col.column_id
WHERE col.object_id = OBJECT_ID('tasks')
  AND col.name = 'status';
EXEC sp_executesql @sql;

-- 2. Tạo CHECK constraint mới
ALTER TABLE tasks ADD CONSTRAINT CK_tasks_status
  CHECK (status IN ('TODO', 'IN_PROGRESS', 'SUBMITTED', 'REVISE', 'DONE'));
