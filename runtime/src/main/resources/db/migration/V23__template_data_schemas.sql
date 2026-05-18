-- Refs #170: schema-validated output data
ALTER TABLE work_item_template ADD COLUMN input_data_schema TEXT;
ALTER TABLE work_item_template ADD COLUMN output_data_schema TEXT;
