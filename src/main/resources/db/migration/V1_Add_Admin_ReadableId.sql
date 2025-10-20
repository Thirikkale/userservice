-- Add readable_id column to admins table
ALTER TABLE admins ADD COLUMN IF NOT EXISTS readable_id VARCHAR(20) UNIQUE;

-- Create index for readable_id for faster lookups
CREATE INDEX IF NOT EXISTS idx_admin_readable_id ON admins(readable_id);

-- Update existing admins with readable IDs (A00001, A00002, etc.)
-- This generates readable IDs based on the row number
WITH numbered_admins AS (
    SELECT 
        admin_id,
        ROW_NUMBER() OVER (ORDER BY created_at) as row_num
    FROM admins
    WHERE readable_id IS NULL
)
UPDATE admins
SET readable_id = CONCAT('A', LPAD(CAST(numbered_admins.row_num AS VARCHAR), 5, '0'))
FROM numbered_admins
WHERE admins.admin_id = numbered_admins.admin_id;
