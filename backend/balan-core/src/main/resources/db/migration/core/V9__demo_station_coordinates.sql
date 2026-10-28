-- 演示场站坐标（杭州滨江），只填充空值，不覆盖运营已维护的坐标
UPDATE station
SET latitude = 30.2084000,
    longitude = 120.2116000
WHERE id = 1
  AND latitude IS NULL
  AND longitude IS NULL;
