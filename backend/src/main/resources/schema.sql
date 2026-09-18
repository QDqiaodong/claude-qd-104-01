SET NAMES utf8mb4;

DROP TABLE IF EXISTS unloading;
DROP TABLE IF EXISTS inspection;
DROP TABLE IF EXISTS shift_record;
DROP TABLE IF EXISTS fuel_gun;
DROP TABLE IF EXISTS tank;

CREATE TABLE tank (
  id BIGINT NOT NULL AUTO_INCREMENT,
  code VARCHAR(32) NOT NULL,
  product VARCHAR(16) NOT NULL,
  capacity INT NOT NULL,
  stock INT NOT NULL,
  safe_stock INT NOT NULL,
  status VARCHAR(16) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_tank_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE fuel_gun (
  id BIGINT NOT NULL AUTO_INCREMENT,
  code VARCHAR(32) NOT NULL,
  machine_no VARCHAR(16) NOT NULL,
  product VARCHAR(16) NOT NULL,
  tank_id BIGINT NOT NULL,
  status VARCHAR(16) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_gun_code (code),
  KEY idx_gun_tank (tank_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE shift_record (
  id BIGINT NOT NULL AUTO_INCREMENT,
  shift_date DATE NOT NULL,
  shift_type VARCHAR(16) NOT NULL,
  gun_id BIGINT NOT NULL,
  start_reading INT NOT NULL,
  end_reading INT NULL,
  volume INT NULL,
  amount INT NULL,
  operator VARCHAR(32) NOT NULL,
  status VARCHAR(16) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_shift_date_type (shift_date, shift_type),
  KEY idx_shift_gun_status (gun_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE unloading (
  id BIGINT NOT NULL AUTO_INCREMENT,
  bill_no VARCHAR(32) NOT NULL,
  plate_no VARCHAR(16) NOT NULL,
  product VARCHAR(16) NOT NULL,
  planned_volume INT NOT NULL,
  tank_id BIGINT NOT NULL,
  status VARCHAR(16) NOT NULL,
  operator VARCHAR(32) NULL,
  bill_date DATE NOT NULL,
  entered_at DATETIME NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_unload_bill_no (bill_no),
  KEY idx_unload_tank_status (tank_id, status),
  KEY idx_unload_date (bill_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE inspection (
  id BIGINT NOT NULL AUTO_INCREMENT,
  inspect_date DATE NOT NULL,
  point VARCHAR(32) NOT NULL,
  result VARCHAR(16) NOT NULL,
  issue_desc VARCHAR(255) NULL,
  inspector VARCHAR(32) NOT NULL,
  status VARCHAR(16) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_inspect_point_date (point, inspect_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO tank (code, product, capacity, stock, safe_stock, status) VALUES
('T-01', '92#', 30000, 13000, 5000, '在用'),
('T-02', '95#', 20000, 9000, 4000, '在用'),
('T-03', '0#', 40000, 32000, 8000, '在用'),
('T-04', '-10#', 20000, 0, 3000, '检修');

INSERT INTO fuel_gun (code, machine_no, product, tank_id, status) VALUES
('G-01', '1号机', '92#', 1, '可用'),
('G-02', '1号机', '95#', 2, '可用'),
('G-03', '2号机', '0#', 3, '可用'),
('G-04', '2号机', '92#', 1, '维修'),
('G-05', '3号机', '0#', 3, '停用');

-- 三个班都挂在 G-01 上，读数首尾相接：114000 → 120000 → 125600 → 当班中
INSERT INTO shift_record (shift_date, shift_type, gun_id, start_reading, end_reading, volume, amount, operator, status) VALUES
('2026-09-16', '白班', 1, 120000, 125600, 5600, 42000, '王小明', '已交接'),
('2026-09-16', '夜班', 1, 125600, NULL, NULL, NULL, '李强', '当班中'),
('2026-09-15', '白班', 1, 114000, 120000, 6000, 45000, '王小明', '已交接');

INSERT INTO inspection (inspect_date, point, result, issue_desc, inspector, status) VALUES
('2026-09-16', 'T-01罐区', '正常', NULL, '王小明', '已记录'),
('2026-09-16', '卸油口', '异常', '卸油口有轻微渗漏', '李强', '待处理'),
('2026-09-15', 'T-01罐区', '正常', NULL, '王小明', '已记录');

-- XY-...-001 已入罐：T-01 期初库存按 13000 升 + 本单 5000 升 = 18000 升对账
INSERT INTO unloading (bill_no, plate_no, product, planned_volume, tank_id, status, operator, bill_date, entered_at) VALUES
('XY-20260916-001', '鲁B·82301', '92#', 5000, 1, '已入罐', '王小明', '2026-09-16', '2026-09-16 09:42:00'),
('XY-20260916-002', '鲁B·61758', '95#', 8000, 2, '待入罐', '李强', '2026-09-16', NULL);
