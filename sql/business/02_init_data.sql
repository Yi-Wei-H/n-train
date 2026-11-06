USE ntrain_business;

INSERT INTO b_station (id, code, name, pinyin) VALUES
(1,  'VNP', '北京南',   'beijingnan'),
(2,  'AOP', '北京丰台', 'beijingfengtai'),
(3,  'JGK', '济南西',   'jinanxi'),
(4,  'UUH', '徐州东',   'xuzhoudong'),
(5,  'BZH', '蚌埠南',   'bengunan'),
(6,  'NJH', '南京南',   'nanjingnan'),
(7,  'SHH', '上海虹桥', 'shanghaihongqiao'),
(8,  'HHC', '合肥南',   'hefeinan'),
(9,  'WHN', '武汉',     'wuhan'),
(10, 'CSQ', '长沙南',   'changshanan'),
(11, 'GZG', '广州南',   'guangzhounan');

-- ---------- 车次与车厢模板（座位可由管理端"生成座位"或每日生成时自动兜底） ----------
INSERT INTO b_train (id, code, train_type) VALUES (1, 'G101', 1), (2, 'G102', 1), (3, 'D901', 2);

-- G101：北京南 → 上海虹桥（经停 5 站，公里数递增）
INSERT INTO b_train_station (id, train_id, station_id, sort, departure_time, arrival_time, distance_km) VALUES
(101, 1, 1, 1, '08:00:00', NULL,        0),
(102, 1, 3, 2, '09:10:00', '09:08:00',  406),
(103, 1, 4, 3, '10:01:00', '09:59:00',  692),
(104, 1, 5, 4, '10:40:00', '10:38:00',  849),
(105, 1, 6, 5, '11:31:00', '11:29:00',  1023),
(106, 1, 7, 6, NULL,       '12:08:00',  1318);

-- G102：上海虹桥 → 北京南（反向）
INSERT INTO b_train_station (id, train_id, station_id, sort, departure_time, arrival_time, distance_km) VALUES
(111, 2, 7, 1, '13:00:00', NULL,        0),
(112, 2, 6, 2, '13:39:00', '13:37:00',  295),
(113, 2, 5, 3, '14:30:00', '14:28:00',  469),
(114, 2, 4, 4, '15:09:00', '15:07:00',  626),
(115, 2, 3, 5, '16:00:00', '15:58:00',  912),
(116, 2, 1, 6, NULL,       '17:06:00',  1318);

-- D901：北京丰台 → 合肥南（经停武汉方向简化）
INSERT INTO b_train_station (id, train_id, station_id, sort, departure_time, arrival_time, distance_km) VALUES
(121, 3, 2, 1, '07:30:00', NULL,        0),
(122, 3, 8, 2, '11:20:00', '11:18:00',  958),
(123, 3, 9, 3, '13:10:00', '13:05:00',  1222),
(124, 3, 10,4, '15:00:00', '14:55:00',  1594);

-- 车厢模板：G101/G102 各 4 节（商务/一等/二等×2），D901 3 节
INSERT INTO b_train_carriage (id, train_id, carriage_no, seat_type, seat_count, row_count) VALUES
(201, 1, 1, 1, 12,  4),
(202, 1, 2, 2, 52,  13),
(203, 1, 3, 3, 85,  17),
(204, 1, 4, 3, 85,  17),
(211, 2, 1, 1, 12,  4),
(212, 2, 2, 2, 52,  13),
(213, 2, 3, 3, 85,  17),
(214, 2, 4, 3, 85,  17),
(221, 3, 1, 2, 52,  13),
(222, 3, 2, 3, 85,  17),
(223, 3, 3, 5, 118, 20);
