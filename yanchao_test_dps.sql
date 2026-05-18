/*
 Navicat Premium Data Transfer

 Source Server         : aurora-test.gonest.cn
 Source Server Type    : MySQL
 Source Server Version : 80039
 Source Host           : aurora-test.gonest.cn:3306
 Source Schema         : yanchao_test_dps

 Target Server Type    : MySQL
 Target Server Version : 80039
 File Encoding         : 65001

 Date: 12/05/2026 09:32:17
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for admin_config
-- ----------------------------
DROP TABLE IF EXISTS `admin_config`;
CREATE TABLE `admin_config`  (
  `user_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `role` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `_created_at` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0),
  `_created_by` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `_updated_at` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0),
  `_updated_by` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  PRIMARY KEY (`user_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of admin_config
-- ----------------------------
INSERT INTO `admin_config` VALUES ('zhou_rong', 'super_admin', '2026-05-10 11:40:16', NULL, '2026-05-10 11:40:16', NULL);

-- ----------------------------
-- Table structure for award_type
-- ----------------------------
DROP TABLE IF EXISTS `award_type`;
CREATE TABLE `award_type`  (
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `scope` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `max_winners` int(0) NULL DEFAULT NULL,
  `sort_order` int(0) NOT NULL DEFAULT 0,
  `is_system` tinyint(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (`code`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of award_type
-- ----------------------------
INSERT INTO `award_type` VALUES ('altruism', '利他奖', 'both', NULL, 30, 1);
INSERT INTO `award_type` VALUES ('excellent', '优秀', 'both', NULL, 40, 1);
INSERT INTO `award_type` VALUES ('monthly_excellence', '月度优秀奖', 'month', NULL, 10, 1);
INSERT INTO `award_type` VALUES ('quarter_star', '季度之星', 'quarter', 1, 20, 1);

-- ----------------------------
-- Table structure for employee_hierarchy
-- ----------------------------
DROP TABLE IF EXISTS `employee_hierarchy`;
CREATE TABLE `employee_hierarchy`  (
  `id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `employee_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `manager_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `dotted_manager_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `department_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `department_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `_created_at` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0),
  `_created_by` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `_updated_at` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0),
  `_updated_by` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `phone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `employee_no` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `employee_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `position` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `work_location` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `join_date` date NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `idx_employee_hierarchy_employee_id`(`employee_id`) USING BTREE,
  INDEX `idx_employee_hierarchy_department_id`(`department_id`) USING BTREE,
  INDEX `idx_employee_hierarchy_dotted_manager_id`(`dotted_manager_id`) USING BTREE,
  INDEX `idx_employee_hierarchy_manager_id`(`manager_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of employee_hierarchy
-- ----------------------------
INSERT INTO `employee_hierarchy` VALUES ('002fabcf-612f-4d2c-b51c-4acb155883ee', 'ou_db419531167b7abf1201d7162a3f444d', 'ou_16ceea5f2a2b934519e4bd6ea0d67f86', 'ou_1c04ffa20d7a3fa98b156a73d0d74bba', NULL, '客服组', '2026-05-11 00:19:31', NULL, '2026-05-11 00:20:12', NULL, '袁冰', '+86 15060034571', '00641', '正式', '客服', '福州', '2022-07-01');
INSERT INTO `employee_hierarchy` VALUES ('00e63e24-037c-433b-9b08-bc04880677a2', 'ou_275ec89ae43633c088353afddde48546', 'ou_101bdabc075f26214e331edf806caeae', NULL, NULL, '市场组', '2026-05-11 00:19:30', NULL, '2026-05-11 00:19:30', NULL, '黎雨湉', '+86 13078664652', '10753', '正式', '市场主管', '深圳', '2023-02-20');
INSERT INTO `employee_hierarchy` VALUES ('03214af3-9149-42f6-9e52-80152c67fc6a', 'ou_ba10c7ead80f882e11449d70d9080e92', 'ou_16ceea5f2a2b934519e4bd6ea0d67f86', NULL, NULL, '客服组', '2026-05-11 00:19:29', NULL, '2026-05-11 00:19:29', NULL, '陈瑶', '+86 13509351527', '10790', '正式', '客服', '福州', '2024-01-25');
INSERT INTO `employee_hierarchy` VALUES ('0c0d503d-aa68-4bcd-bb0c-a8f669a39cc1', 'ou_9ed535ad58bbbc93af5135824859c765', 'ou_1edb87554c088bae740b33ed93fc3a0a', NULL, NULL, '财务部', '2026-05-11 00:19:28', NULL, '2026-05-11 00:19:28', NULL, '孙源襄', '+86 13410221679', '10836', '正式', '会计主管', '深圳', '2024-10-14');
INSERT INTO `employee_hierarchy` VALUES ('0da166ba-a121-4fda-8a6f-ba85e3b929e6', 'ou_4c11e99fb804eed678c04c5cd74e9ca8', 'ou_1edb87554c088bae740b33ed93fc3a0a', NULL, NULL, '财务部', '2026-05-11 00:19:26', NULL, '2026-05-11 00:19:26', NULL, '李延菊', '+86 13537836034', '11052', '正式', '财务经理', NULL, '2026-01-04');
INSERT INTO `employee_hierarchy` VALUES ('0ecc3154-0f3e-4ca6-868f-9363f75aca9f', 'ou_fcca34b19da5a1ece6f680b5a050c49a', 'ou_2b5b9dfb51e6616832b03f1a13838f02', NULL, NULL, '流量运营组', '2026-05-11 00:19:26', NULL, '2026-05-11 00:19:26', NULL, '温恒远', '+86 18811822376', '11059', '正式', 'SEO', '深圳', '2026-02-02');
INSERT INTO `employee_hierarchy` VALUES ('0f9acfd9-ae76-4fa5-992e-e801560ab890', 'ou_ebd915516304b99ad045367c232a417e', 'ou_16ceea5f2a2b934519e4bd6ea0d67f86', NULL, NULL, '客服组', '2026-05-11 00:19:26', NULL, '2026-05-11 00:19:26', NULL, '叶君婕', '+86 13923894634', '11075', '正式', '客服', '福州', '2026-03-16');
INSERT INTO `employee_hierarchy` VALUES ('11111111-1111-4111-8111-111111111111', 'zhou_rong', 'demo_manager', 'demo_emp_02', 'dept-default', '管理部', '2026-05-10 11:58:06', NULL, '2026-05-10 23:41:00', NULL, '周荣', NULL, NULL, NULL, '员工', NULL, NULL);
INSERT INTO `employee_hierarchy` VALUES ('15adce0f-ee75-4b5f-ae23-96276d762ff5', 'ou_22d3a84d0721bd55fbfe307243775648', 'ou_a9e2297ee31fa723fc2c97a150bcbecf', NULL, NULL, '引擎组', '2026-05-11 00:19:31', NULL, '2026-05-11 00:19:31', NULL, '庄宝山', '+86 18876308183', '00159', '正式', '高级C++开发工程师', '厦门', '2022-07-01');
INSERT INTO `employee_hierarchy` VALUES ('192d6ae7-d160-460e-8d3a-a1540fb71ad2', 'ou_78f026d2623075a20af0bc91acde149b', 'ou_22d3a84d0721bd55fbfe307243775648', NULL, NULL, '引擎组', '2026-05-11 00:19:26', NULL, '2026-05-11 00:19:26', NULL, '王天磊', '+86 15882274813', NULL, '外包', '外聘-高级C++开发工程师', NULL, '2025-12-16');
INSERT INTO `employee_hierarchy` VALUES ('22222222-2222-4222-8222-222222222222', 'demo_manager', NULL, NULL, 'dept-default', '管理部', '2026-05-10 11:56:58', NULL, '2026-05-10 11:56:58', NULL, '演示经理', NULL, NULL, NULL, '经理', NULL, NULL);
INSERT INTO `employee_hierarchy` VALUES ('2430ced4-8a45-4391-8258-948362905ddc', 'ou_421051599a16e7c496cb330b07aaa23a', 'ou_101bdabc075f26214e331edf806caeae', NULL, NULL, '市场组', '2026-05-11 00:19:29', NULL, '2026-05-11 00:19:29', NULL, '陈伟建', '+86 15859814305', '10791', '正式', '用户增长', '厦门', '2024-02-21');
INSERT INTO `employee_hierarchy` VALUES ('279f7499-5501-4ca3-afae-d692337508de', 'ou_96b6a7bdc08e526689b3b0bc56fc355b', 'ou_101bdabc075f26214e331edf806caeae', NULL, NULL, '市场组', '2026-05-11 00:19:25', NULL, '2026-05-11 00:19:25', NULL, '林惠', '+86 15606902596', '11084', '正式', '用户增长', '厦门', '2026-04-07');
INSERT INTO `employee_hierarchy` VALUES ('2931cfbd-0c3b-4dab-b49a-20154f28f9c1', 'ou_547db352b2a62656356069864b0258a4', 'ou_9ed535ad58bbbc93af5135824859c765', NULL, NULL, '财务部', '2026-05-11 00:19:28', NULL, '2026-05-11 00:19:28', NULL, '傅金艳', '+86 15283890120', '10843', '正式', '会计助理', '深圳', '2024-11-11');
INSERT INTO `employee_hierarchy` VALUES ('308c9f86-0b88-432e-90b2-689ab0d73456', 'qa_admin', 'demo_manager', NULL, NULL, '管理部', '2026-05-12 09:27:01', NULL, '2026-05-12 09:27:01', NULL, 'QA管理员', NULL, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `employee_hierarchy` VALUES ('33333333-3333-4333-8333-333333333333', 'demo_emp_01', 'demo_manager', NULL, 'dept-default', '管理部', '2026-05-10 20:15:05', NULL, '2026-05-10 20:15:05', NULL, '张三', NULL, NULL, NULL, '员工', NULL, NULL);
INSERT INTO `employee_hierarchy` VALUES ('3f0e81c7-79a3-4a91-85df-8ca6496108f9', 'ou_0ca3acb18db064962a05948152bdfa85', 'ou_16ceea5f2a2b934519e4bd6ea0d67f86', NULL, NULL, '运营组', '2026-05-11 00:19:29', NULL, '2026-05-11 00:19:29', NULL, '季昕', '+86 18960783371', '10783', '正式', '用户运营', '福州', '2024-08-05');
INSERT INTO `employee_hierarchy` VALUES ('44202693-b028-490f-b8c7-c4d23de4e0c6', 'demo_dotted_mgr', NULL, NULL, NULL, '管理部', '2026-05-12 09:27:01', NULL, '2026-05-12 09:27:01', NULL, '演示虚线上级', NULL, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `employee_hierarchy` VALUES ('44444444-4444-4444-8444-444444444444', 'demo_emp_02', 'demo_manager', NULL, 'dept-dev', '管理部', '2026-05-10 20:15:05', NULL, '2026-05-12 09:27:17', NULL, '李四', NULL, NULL, NULL, '员工', NULL, NULL);
INSERT INTO `employee_hierarchy` VALUES ('44f62019-de0f-4f04-b81a-011f6cbbd26f', 'ou_85ea4d1af757927e6eef04ca50bcee48', 'ou_1c04ffa20d7a3fa98b156a73d0d74bba', NULL, NULL, '内容组', '2026-05-11 00:19:27', NULL, '2026-05-11 00:19:27', NULL, '郭炜棠', '+86 13510378035', '11047', '实习', '内容运营实习生', '深圳', '2025-12-01');
INSERT INTO `employee_hierarchy` VALUES ('4885acaa-86a8-476f-a225-ab2d56ab2370', 'ou_754baa28177c2666a537fb702a286192', 'ou_64601fdbfef561244d09c4c4df3866ca', NULL, NULL, '雁巢研发部', '2026-05-11 00:19:30', NULL, '2026-05-11 00:19:30', NULL, '周荣', '+86 15022775697', '10751', '正式', 'Java开发工程师', '福州', '2023-02-13');
INSERT INTO `employee_hierarchy` VALUES ('4cd51d26-4fb1-487d-a140-cf24f23f8ddb', 'ou_e1d1fd5830c619c8235c372d596c8de4', 'ou_43f02ee31bbb7485f307acd8de37af70', NULL, NULL, '应用组', '2026-05-11 00:19:31', NULL, '2026-05-11 00:19:31', NULL, '张君泽', '+86 15659891760', '00085', '正式', 'iOS开发工程师', '福州', '2022-07-01');
INSERT INTO `employee_hierarchy` VALUES ('4d4cbd46-685e-4b43-b12b-9fad28157fd4', 'ou_a9e2297ee31fa723fc2c97a150bcbecf', 'ou_de36396e25ed1528abf0c035aa098aab', NULL, NULL, '产品研发中心', '2026-05-11 00:19:32', NULL, '2026-05-11 00:19:32', NULL, 'Meta', '+86 18073144211', '00131', '正式', '副总经理', '福州', '2022-07-01');
INSERT INTO `employee_hierarchy` VALUES ('4e5f715e-8df8-47e6-9fbe-5212ae26b7f0', 'ou_54986f65ebbcbe5c15075506460c5a26', 'ou_de36396e25ed1528abf0c035aa098aab', NULL, NULL, 'DiffMind', '2026-05-11 00:19:32', NULL, '2026-05-11 00:19:32', NULL, 'Chuang', '+86 15516853888', '00191', '正式', '项目总监', '深圳', '2022-07-01');
INSERT INTO `employee_hierarchy` VALUES ('53733da8-12da-4173-8cf1-f1bb7b603fca', 'ou_16ceea5f2a2b934519e4bd6ea0d67f86', 'ou_1c04ffa20d7a3fa98b156a73d0d74bba', NULL, NULL, '运营组', '2026-05-11 00:19:32', NULL, '2026-05-11 00:19:32', NULL, '郑智慧', '+86 18559175762', '00145', '正式', '运营主管', '福州', '2022-07-01');
INSERT INTO `employee_hierarchy` VALUES ('59e57327-bdf9-44ca-a1a2-e6780d07d30e', 'ou_31b240a2812b722644b20d2f50bead68', 'ou_1edb87554c088bae740b33ed93fc3a0a', NULL, NULL, '行政组', '2026-05-11 00:19:28', NULL, '2026-05-11 00:19:28', NULL, '李莉莉', '+86 18750014051', '11002', '正式', '行政主管', '福州', '2025-02-24');
INSERT INTO `employee_hierarchy` VALUES ('5d213624-41ba-4683-8d67-a403ffd2031a', 'ou_c453c4ec158fa49625f5338938cd237a', 'ou_43f02ee31bbb7485f307acd8de37af70', NULL, NULL, '应用组', '2026-05-11 00:19:29', NULL, '2026-05-11 00:19:29', NULL, '汤俊杰', '+86 13141316507', '10800', '正式', 'Android开发工程师', '福州', '2024-04-15');
INSERT INTO `employee_hierarchy` VALUES ('5d96445c-d784-47cd-88d1-bf1de724c81c', 'ou_1edb87554c088bae740b33ed93fc3a0a', 'ou_de36396e25ed1528abf0c035aa098aab', NULL, NULL, '财务部', '2026-05-11 00:19:32', NULL, '2026-05-11 00:19:32', NULL, '连大赢', '+86 15080018191', '00018', '正式', 'CTO', '福州', '2022-07-01');
INSERT INTO `employee_hierarchy` VALUES ('6094bf70-aa23-4eae-b5b4-41a40d09c403', 'ou_e189a563fcfc5be9e0ba1f6a56ac812b', 'ou_22d3a84d0721bd55fbfe307243775648', NULL, NULL, '引擎组', '2026-05-11 00:19:30', NULL, '2026-05-11 00:19:30', NULL, '龚文超', '+86 19905919872', '00313', '正式', '网络开发工程师', '福州', '2022-07-01');
INSERT INTO `employee_hierarchy` VALUES ('6c64b178-9308-47bd-a2d0-ce3955995a6e', 'ou_e5a172693851782827bff11204fc5510', 'ou_c9bd27e5e69abe3c1aa5a72fc81084a1', 'ou_31b240a2812b722644b20d2f50bead68', NULL, '人事组', '2026-05-11 00:19:26', NULL, '2026-05-11 00:19:26', NULL, '周可怡', '+86 15986006539', '11058', '正式', '人事行政专员', '深圳', '2026-02-02');
INSERT INTO `employee_hierarchy` VALUES ('70f7b145-cd22-433b-aa1b-95d619c704b9', 'ou_e5673bb4481b47c601c6c2f0af83b9f8', 'ou_54986f65ebbcbe5c15075506460c5a26', NULL, NULL, 'DiffMind研发部', '2026-05-11 00:19:31', NULL, '2026-05-11 00:19:31', NULL, '邓小狼', '+86 13003849846', '00685', '正式', '技术负责人', '福州', '2022-07-01');
INSERT INTO `employee_hierarchy` VALUES ('747319e2-e2bb-4384-9845-e2426f026fa6', 'ou_cbb7d488560bfcd510acc3e8a1f985f4', 'ou_1c04ffa20d7a3fa98b156a73d0d74bba', NULL, NULL, '市场组', '2026-05-11 00:19:25', NULL, '2026-05-11 00:19:25', NULL, '陈冬青', '+86 17355412008', '11085', '实习', '用户增长实习生', '深圳', '2026-04-08');
INSERT INTO `employee_hierarchy` VALUES ('78962b25-b182-4951-b082-9fb8d8cc5411', 'ou_d78f9cf7beed7cb89b5142b4a461331a', 'ou_101bdabc075f26214e331edf806caeae', NULL, NULL, '市场组', '2026-05-11 00:19:27', NULL, '2026-05-11 00:19:27', NULL, '洪丹蕊', '+86 13425199820', '11024', '正式', '用户增长', '深圳', '2025-06-23');
INSERT INTO `employee_hierarchy` VALUES ('7a1ddcf0-47ab-482c-8e9c-cd07cdc9d652', 'ou_17c9bd555bb1f7622af2e9d2e5121c3a', 'ou_1edb87554c088bae740b33ed93fc3a0a', NULL, NULL, '薪酬福利组', '2026-05-11 00:19:32', NULL, '2026-05-11 00:19:32', NULL, '张衡', '+86 18124580860', '00474', '正式', '数据分析师', '深圳', '2022-07-01');
INSERT INTO `employee_hierarchy` VALUES ('7c4173e5-ecbf-4505-a9a2-4274348ba265', 'ou_33169365ff93c61b8328ea0301773a05', 'ou_a9e2297ee31fa723fc2c97a150bcbecf', NULL, NULL, '产品组', '2026-05-11 00:19:31', NULL, '2026-05-11 00:19:31', NULL, '方莉岚', '+86 15880078231', '00219', '正式', 'UI设计师', '福州', '2022-07-01');
INSERT INTO `employee_hierarchy` VALUES ('7c9a8497-e7c5-459a-96a5-f07145703a49', 'ou_804c264aab7ac2c69d64f85adc7c1ecb', 'ou_2b5b9dfb51e6616832b03f1a13838f02', NULL, NULL, '流量运营组', '2026-05-11 00:19:27', NULL, '2026-05-11 00:19:27', NULL, '程柯', '+86 15884734842', '11043', '正式', '信息流优化师', '深圳', '2025-11-13');
INSERT INTO `employee_hierarchy` VALUES ('7eb179cc-081a-43fc-9eec-f40322aca231', 'qa_emp_dotted', 'demo_manager', 'demo_dotted_mgr', NULL, '管理部', '2026-05-12 09:27:01', NULL, '2026-05-12 09:27:01', NULL, '虚线链路员工', NULL, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `employee_hierarchy` VALUES ('817ef321-e02d-44e9-b789-fd1c175cfd31', 'ou_bdfb0263ab96ca86085d907429a8d317', 'ou_de36396e25ed1528abf0c035aa098aab', NULL, NULL, '前线委员会', '2026-05-11 00:19:31', NULL, '2026-05-11 00:19:31', NULL, 'Roy', '+86 17690724709', '00118', '正式', '项目总监', '深圳', '2022-07-01');
INSERT INTO `employee_hierarchy` VALUES ('8533946d-147c-41d4-8289-61bf6c90a050', 'ou_01faf6529f892d24ddadf3463e3ce2f1', 'ou_54986f65ebbcbe5c15075506460c5a26', NULL, NULL, '市场运营部', '2026-05-11 00:19:28', NULL, '2026-05-11 00:19:28', NULL, 'Wyman', '+86 13431959501', '10827', '正式', '市场主管', '深圳', '2024-09-02');
INSERT INTO `employee_hierarchy` VALUES ('91c7c570-04d6-41cf-8673-de12444ea583', 'ou_0a9bf8d85518cb8d8719be17aa9f8fff', 'ou_43f02ee31bbb7485f307acd8de37af70', NULL, NULL, '应用组', '2026-05-11 00:19:29', NULL, '2026-05-11 00:19:29', NULL, '黄倩', '+86 18705918501', '10777', '正式', '测试工程师', '福州', '2023-07-17');
INSERT INTO `employee_hierarchy` VALUES ('92b6e061-7f68-4143-b498-f34c69962c48', 'ou_e611d0331f73c5a76095f94f609c1db6', 'ou_43f02ee31bbb7485f307acd8de37af70', NULL, NULL, '应用组', '2026-05-11 00:19:30', NULL, '2026-05-11 00:19:30', NULL, '林程', '+86 18850799100', '00061', '正式', 'Java开发工程师', '福州', '2022-07-01');
INSERT INTO `employee_hierarchy` VALUES ('98c1b832-fb47-4c6b-8e4d-3a711d5008d0', 'ou_ecfc9cd557e14495fa988e3f1f39720b', 'ou_64601fdbfef561244d09c4c4df3866ca', NULL, NULL, '雁巢研发部', '2026-05-11 00:19:29', NULL, '2026-05-11 00:19:29', NULL, '林功晨', '+86 13123396250', '10802', '正式', '前端开发工程师', '福州', '2024-04-28');
INSERT INTO `employee_hierarchy` VALUES ('a0b59f9a-0bd2-4f9c-83df-8a5e1cb750f3', 'ou_163f7854da693143b952ff3e9c4975a2', 'ou_1edb87554c088bae740b33ed93fc3a0a', NULL, NULL, '财务部', '2026-05-11 00:19:27', NULL, '2026-05-11 00:19:27', NULL, '李梦薇', '+86 15205035966', '11048', '正式', '会计', '福州', '2025-12-08');
INSERT INTO `employee_hierarchy` VALUES ('a2c51ce0-1e37-46be-8349-94b6d87c27d1', 'ou_52018ca138fbabc571389fa7b49e416e', 'ou_e5673bb4481b47c601c6c2f0af83b9f8', NULL, NULL, 'DiffMind研发部', '2026-05-11 00:19:25', NULL, '2026-05-11 00:19:25', NULL, '郑建伟', '+86 15159574252', '11083', '正式', 'Java工程师', '福州西塔', '2026-04-07');
INSERT INTO `employee_hierarchy` VALUES ('a3471576-7357-40fa-990f-f68ccb77d4f1', 'ou_de36396e25ed1528abf0c035aa098aab', NULL, NULL, NULL, '前线委员会', '2026-05-11 00:19:32', NULL, '2026-05-11 00:19:32', NULL, '余仕林', '+86 18503088429', '00075', '正式', 'CEO', '深圳', '2022-07-01');
INSERT INTO `employee_hierarchy` VALUES ('a6a5ec00-4fff-4cde-b1ed-db39dc18fb29', 'ou_dccd234fdce4b4b596bf53775cc5f507', 'ou_43f02ee31bbb7485f307acd8de37af70', NULL, NULL, '应用组', '2026-05-11 00:19:29', NULL, '2026-05-11 00:19:29', NULL, '黄文飞', '+86 17720835576', '10796', '正式', '前端开发工程师', '福州', '2024-03-13');
INSERT INTO `employee_hierarchy` VALUES ('a6f85371-85f7-4985-8ff3-7c75a6fa43bc', 'ou_79bdce08f49ed99ba7aa1a94a46bc89e', 'ou_16ceea5f2a2b934519e4bd6ea0d67f86', NULL, NULL, '客服组', '2026-05-11 00:19:27', NULL, '2026-05-11 00:19:27', NULL, '涂瀅东', '+86 15980257213', '11020', '正式', '客服', '福州', '2025-05-26');
INSERT INTO `employee_hierarchy` VALUES ('a7110c2f-8aba-476c-8a07-9f25d9604f8c', 'ou_43f02ee31bbb7485f307acd8de37af70', 'ou_a9e2297ee31fa723fc2c97a150bcbecf', NULL, NULL, '应用组', '2026-05-11 00:19:30', NULL, '2026-05-11 00:19:30', NULL, 'Jie', '+86 15060137970', '00099', '正式', '技术负责人', '福州', '2022-07-01');
INSERT INTO `employee_hierarchy` VALUES ('a882839d-a232-4f8a-a5da-85ad46a0a246', 'ou_5f0b6e3f523b785a54ad4cb09c563102', 'ou_16ceea5f2a2b934519e4bd6ea0d67f86', NULL, NULL, '运营组', '2026-05-11 00:19:28', NULL, '2026-05-11 00:19:28', NULL, '方宏超', '+86 13763830681', '11021', '正式', '平面设计师', '福州', '2025-05-26');
INSERT INTO `employee_hierarchy` VALUES ('b3e77190-8d0a-48b4-b4d8-f21d44cf521c', 'ou_f4a65680b8c9129a8bd71da6adab056b', 'ou_16ceea5f2a2b934519e4bd6ea0d67f86', NULL, NULL, '运营组', '2026-05-11 00:19:27', NULL, '2026-05-11 00:19:27', NULL, '伍博', '+86 18312135525', '11022', '正式', '私域运营', '深圳', '2025-05-27');
INSERT INTO `employee_hierarchy` VALUES ('b7280a6d-cc41-4243-8742-cf9e23be4894', 'ou_2b5b9dfb51e6616832b03f1a13838f02', 'ou_1c04ffa20d7a3fa98b156a73d0d74bba', NULL, NULL, '流量运营组', '2026-05-11 00:19:30', NULL, '2026-05-11 00:19:30', NULL, '钟岳锋', '+86 13246151428', '10763', '正式', '流量运营主管', '深圳', '2023-04-18');
INSERT INTO `employee_hierarchy` VALUES ('b7931ddb-0413-413d-8349-d9d73791a70c', 'ou_d4a8fb886747a4262fb51d5f753f8760', 'ou_101bdabc075f26214e331edf806caeae', NULL, NULL, '市场组', '2026-05-11 00:19:25', NULL, '2026-05-11 00:19:25', NULL, '张鹏', '+86 18008478221', '11086', '正式', '用户增长', '长沙', '2026-04-08');
INSERT INTO `employee_hierarchy` VALUES ('ba638935-04da-4413-945f-8575d361eea3', 'ou_bb7d137e94dd918f966bd7798840b46d', 'ou_22d3a84d0721bd55fbfe307243775648', NULL, NULL, '引擎组', '2026-05-11 00:19:28', NULL, '2026-05-11 00:19:28', NULL, '蔡华坤', '+86 13626087888', '10787', '正式', '游戏测试工程师', '厦门翔安创新创业中心10楼', '2024-08-05');
INSERT INTO `employee_hierarchy` VALUES ('c4188ff5-de16-4fbf-b258-21fc4f4136ad', 'ou_a82399fe61204e602f07671dcd9b17a8', 'ou_1c04ffa20d7a3fa98b156a73d0d74bba', NULL, NULL, '品牌策划组', '2026-05-11 00:19:26', NULL, '2026-05-11 00:19:26', NULL, '喻政翰', '+86 13923482151', '11074', '正式', '品牌策划', '深圳福永意库', '2026-03-16');
INSERT INTO `employee_hierarchy` VALUES ('ca8bb0a3-ae22-40f2-b99c-b5987a3f74c1', 'ou_7dc9617b06ce7560e3222ac7ab9934f0', 'ou_a9e2297ee31fa723fc2c97a150bcbecf', NULL, NULL, '产品组', '2026-05-11 00:19:30', NULL, '2026-05-11 00:19:30', NULL, '杨丹华', '+86 15280099801', '10769', '正式', '产品经理', '福州', '2023-05-18');
INSERT INTO `employee_hierarchy` VALUES ('d13e8a3e-2bcb-4872-8f8e-06a641a5d2f1', 'ou_c9bd27e5e69abe3c1aa5a72fc81084a1', 'ou_1edb87554c088bae740b33ed93fc3a0a', NULL, NULL, '人事组', '2026-05-11 00:19:28', NULL, '2026-05-11 00:19:28', NULL, '连道君', '+86 15574808492', '11014', '正式', 'HRBP', '深圳', '2025-05-12');
INSERT INTO `employee_hierarchy` VALUES ('d195ec59-f6b1-42b8-a6d4-95a7fabd3771', 'ou_336ab6132200ae15efd26c63ef71e206', 'ou_1edb87554c088bae740b33ed93fc3a0a', NULL, NULL, '采购部', '2026-05-11 00:19:26', NULL, '2026-05-11 00:19:26', NULL, '周梦婷', '+86 13365997027', '11067', '正式', '项目管理', '福州', '2026-03-04');
INSERT INTO `employee_hierarchy` VALUES ('d491c11e-ea1a-4586-91b0-e9f064949d31', 'ou_101bdabc075f26214e331edf806caeae', 'ou_1c04ffa20d7a3fa98b156a73d0d74bba', NULL, NULL, '市场组', '2026-05-11 00:19:29', NULL, '2026-05-11 00:19:29', NULL, '刘威', '+86 13959625776', '10792', '正式', '市场副总监（代）', '厦门', '2024-03-04');
INSERT INTO `employee_hierarchy` VALUES ('dd1560a6-e25f-44ec-ad8c-11755d73ce0a', 'ou_07d83f8f60152a48ca3dc00c265e1922', 'ou_bdfb0263ab96ca86085d907429a8d317', NULL, NULL, '市场运营族', '2026-05-11 00:19:27', NULL, '2026-05-11 00:19:27', NULL, '刘承靓', '+86 15259871302', '11030', '正式', '用户增长', '深圳', '2025-07-02');
INSERT INTO `employee_hierarchy` VALUES ('e743d1c8-ccad-4ca0-83bd-c33f9dbe101b', 'ou_fe559874a6b80e59a18cffcee7b55399', 'ou_54986f65ebbcbe5c15075506460c5a26', 'ou_a9e2297ee31fa723fc2c97a150bcbecf', NULL, 'DiffMind研发部', '2026-05-11 00:19:25', NULL, '2026-05-11 00:19:25', NULL, '段嘉', '+86 13168740076', NULL, '正式', '用户体验师', NULL, '2026-04-01');
INSERT INTO `employee_hierarchy` VALUES ('e9f69177-f610-4e60-abb1-3198e19f0fef', 'ou_64601fdbfef561244d09c4c4df3866ca', 'ou_a9e2297ee31fa723fc2c97a150bcbecf', NULL, NULL, '雁巢研发部', '2026-05-11 00:19:31', NULL, '2026-05-11 00:19:31', NULL, 'CC', '+86 13225998510', '00158', '正式', '产品经理', '福州', '2022-07-01');
INSERT INTO `employee_hierarchy` VALUES ('eed68e22-f967-4787-aeaa-d31c54924f9b', 'ou_40035a90c42c1c2d283593052402bc44', 'ou_64601fdbfef561244d09c4c4df3866ca', NULL, NULL, '雁巢研发部', '2026-05-11 00:19:30', NULL, '2026-05-11 00:19:30', NULL, '陈劭弘', '+86 15005930781', '00143', '正式', '测试工程师', '厦门', '2022-07-01');
INSERT INTO `employee_hierarchy` VALUES ('ef66edf6-42a1-4ac3-9e48-57bd495b220f', 'ou_1c04ffa20d7a3fa98b156a73d0d74bba', 'ou_de36396e25ed1528abf0c035aa098aab', NULL, NULL, 'QuickFox', '2026-05-11 00:19:31', NULL, '2026-05-11 00:19:31', NULL, 'Jason', '+86 15507580541', '00206', '正式', '项目总监', '深圳', '2022-07-01');
INSERT INTO `employee_hierarchy` VALUES ('efbcbea9-1d87-4eaa-8e07-10d5db515035', 'ou_da93d1e4769b4ad8b5224bd91de02eb2', 'ou_bdfb0263ab96ca86085d907429a8d317', NULL, NULL, '采购部', '2026-05-11 00:19:28', NULL, '2026-05-11 00:19:28', NULL, '劳栋良', '+86 13427176179', '11015', '正式', '物流仓库主管', '深圳', '2025-05-12');
INSERT INTO `employee_hierarchy` VALUES ('f4ab440f-1b38-4953-bb1a-1be2d012027e', 'ou_8b12741f55f088a80eba76cd010f6df0', 'ou_16ceea5f2a2b934519e4bd6ea0d67f86', NULL, NULL, '客服组', '2026-05-11 00:19:27', NULL, '2026-05-11 00:19:27', NULL, '余丽芳', '+86 15860851071', '11016', '正式', '客服', '厦门', '2025-07-01');

-- ----------------------------
-- Table structure for evaluation_period
-- ----------------------------
DROP TABLE IF EXISTS `evaluation_period`;
CREATE TABLE `evaluation_period`  (
  `id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `period_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `period_key` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '',
  `sort_order` int(0) NOT NULL DEFAULT 0,
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'open',
  `parent_period_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `_created_at` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0),
  `_updated_at` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0),
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uq_evaluation_period_type_key`(`period_type`, `period_key`) USING BTREE,
  INDEX `idx_evaluation_period_parent`(`parent_period_id`) USING BTREE,
  CONSTRAINT `fk_evaluation_period_parent` FOREIGN KEY (`parent_period_id`) REFERENCES `evaluation_period` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of evaluation_period
-- ----------------------------
INSERT INTO `evaluation_period` VALUES ('a1000000-0000-4000-8000-260201000001', 'quarter', '2026-Q1', '2026年第一季度', 1, 'open', NULL, '2026-05-10 15:27:45', '2026-05-10 15:27:45');
INSERT INTO `evaluation_period` VALUES ('a1000000-0000-4000-8000-260202000001', 'quarter', '2026-Q2', '2026年第二季度', 2, 'open', NULL, '2026-05-10 15:27:45', '2026-05-10 15:27:45');
INSERT INTO `evaluation_period` VALUES ('a1000000-0000-4000-8000-260203000001', 'quarter', '2026-Q3', '2026年第三季度', 3, 'open', NULL, '2026-05-10 15:27:45', '2026-05-10 15:27:45');
INSERT INTO `evaluation_period` VALUES ('a1000000-0000-4000-8000-260204000001', 'quarter', '2026-Q4', '2026年第四季度', 4, 'open', NULL, '2026-05-10 15:27:45', '2026-05-10 15:27:45');
INSERT INTO `evaluation_period` VALUES ('a1000000-0000-4000-8000-270201000001', 'quarter', '2027-Q1', '2027年第一季度', 5, 'open', NULL, '2026-05-10 15:27:45', '2026-05-10 15:27:45');
INSERT INTO `evaluation_period` VALUES ('a1000000-0000-4000-8000-270202000001', 'quarter', '2027-Q2', '2027年第二季度', 6, 'open', NULL, '2026-05-10 15:27:45', '2026-05-10 15:27:45');
INSERT INTO `evaluation_period` VALUES ('a1000000-0000-4000-8000-270203000001', 'quarter', '2027-Q3', '2027年第三季度', 7, 'open', NULL, '2026-05-10 15:27:45', '2026-05-10 15:27:45');
INSERT INTO `evaluation_period` VALUES ('a1000000-0000-4000-8000-270204000001', 'quarter', '2027-Q4', '2027年第四季度', 8, 'open', NULL, '2026-05-10 15:27:45', '2026-05-10 15:27:45');
INSERT INTO `evaluation_period` VALUES ('b2000000-0000-4000-8000-202603000001', 'month', '2026-03', '2026年3月', 3, 'open', 'a1000000-0000-4000-8000-260201000001', '2026-05-10 15:27:45', '2026-05-10 15:27:45');
INSERT INTO `evaluation_period` VALUES ('b2000000-0000-4000-8000-202604000001', 'month', '2026-04', '2026年4月', 4, 'open', 'a1000000-0000-4000-8000-260202000001', '2026-05-10 15:27:45', '2026-05-10 15:27:45');
INSERT INTO `evaluation_period` VALUES ('b2000000-0000-4000-8000-202605000001', 'month', '2026-05', '2026年5月', 5, 'open', 'a1000000-0000-4000-8000-260202000001', '2026-05-10 15:27:45', '2026-05-10 15:27:45');
INSERT INTO `evaluation_period` VALUES ('b2000000-0000-4000-8000-202606000001', 'month', '2026-06', '2026年6月', 6, 'open', 'a1000000-0000-4000-8000-260202000001', '2026-05-10 15:27:45', '2026-05-10 15:27:45');
INSERT INTO `evaluation_period` VALUES ('b2000000-0000-4000-8000-202607000001', 'month', '2026-07', '2026年7月', 7, 'open', 'a1000000-0000-4000-8000-260203000001', '2026-05-10 15:27:45', '2026-05-10 15:27:45');
INSERT INTO `evaluation_period` VALUES ('b2000000-0000-4000-8000-202608000001', 'month', '2026-08', '2026年8月', 8, 'open', 'a1000000-0000-4000-8000-260203000001', '2026-05-10 15:27:45', '2026-05-10 15:27:45');
INSERT INTO `evaluation_period` VALUES ('b2000000-0000-4000-8000-202609000001', 'month', '2026-09', '2026年9月', 9, 'open', 'a1000000-0000-4000-8000-260203000001', '2026-05-10 15:27:45', '2026-05-10 15:27:45');
INSERT INTO `evaluation_period` VALUES ('b2000000-0000-4000-8000-202610000001', 'month', '2026-10', '2026年10月', 10, 'open', 'a1000000-0000-4000-8000-260204000001', '2026-05-10 15:27:45', '2026-05-10 15:27:45');
INSERT INTO `evaluation_period` VALUES ('b2000000-0000-4000-8000-202611000001', 'month', '2026-11', '2026年11月', 11, 'open', 'a1000000-0000-4000-8000-260204000001', '2026-05-10 15:27:45', '2026-05-10 15:27:45');
INSERT INTO `evaluation_period` VALUES ('b2000000-0000-4000-8000-202612000001', 'month', '2026-12', '2026年12月', 12, 'open', 'a1000000-0000-4000-8000-260204000001', '2026-05-10 15:27:45', '2026-05-10 15:27:45');
INSERT INTO `evaluation_period` VALUES ('b2000000-0000-4000-8000-202701000001', 'month', '2027-01', '2027年1月', 13, 'open', 'a1000000-0000-4000-8000-270201000001', '2026-05-10 15:27:45', '2026-05-10 15:27:45');
INSERT INTO `evaluation_period` VALUES ('b2000000-0000-4000-8000-202702000001', 'month', '2027-02', '2027年2月', 14, 'open', 'a1000000-0000-4000-8000-270201000001', '2026-05-10 15:27:45', '2026-05-10 15:27:45');
INSERT INTO `evaluation_period` VALUES ('b2000000-0000-4000-8000-202703000001', 'month', '2027-03', '2027年3月', 15, 'open', 'a1000000-0000-4000-8000-270201000001', '2026-05-10 15:27:45', '2026-05-10 15:27:45');
INSERT INTO `evaluation_period` VALUES ('b2000000-0000-4000-8000-202704000001', 'month', '2027-04', '2027年4月', 16, 'open', 'a1000000-0000-4000-8000-270202000001', '2026-05-10 15:27:45', '2026-05-10 15:27:45');
INSERT INTO `evaluation_period` VALUES ('b2000000-0000-4000-8000-202705000001', 'month', '2027-05', '2027年5月', 17, 'open', 'a1000000-0000-4000-8000-270202000001', '2026-05-10 15:27:45', '2026-05-10 15:27:45');
INSERT INTO `evaluation_period` VALUES ('b2000000-0000-4000-8000-202706000001', 'month', '2027-06', '2027年6月', 18, 'open', 'a1000000-0000-4000-8000-270202000001', '2026-05-10 15:27:45', '2026-05-10 15:27:45');
INSERT INTO `evaluation_period` VALUES ('b2000000-0000-4000-8000-202707000001', 'month', '2027-07', '2027年7月', 19, 'open', 'a1000000-0000-4000-8000-270203000001', '2026-05-10 15:27:45', '2026-05-10 15:27:45');
INSERT INTO `evaluation_period` VALUES ('b2000000-0000-4000-8000-202708000001', 'month', '2027-08', '2027年8月', 20, 'open', 'a1000000-0000-4000-8000-270203000001', '2026-05-10 15:27:45', '2026-05-10 15:27:45');
INSERT INTO `evaluation_period` VALUES ('b2000000-0000-4000-8000-202709000001', 'month', '2027-09', '2027年9月', 21, 'open', 'a1000000-0000-4000-8000-270203000001', '2026-05-10 15:27:45', '2026-05-10 15:27:45');
INSERT INTO `evaluation_period` VALUES ('b2000000-0000-4000-8000-202710000001', 'month', '2027-10', '2027年10月', 22, 'open', 'a1000000-0000-4000-8000-270204000001', '2026-05-10 15:27:45', '2026-05-10 15:27:45');
INSERT INTO `evaluation_period` VALUES ('b2000000-0000-4000-8000-202711000001', 'month', '2027-11', '2027年11月', 23, 'open', 'a1000000-0000-4000-8000-270204000001', '2026-05-10 15:27:45', '2026-05-10 15:27:45');
INSERT INTO `evaluation_period` VALUES ('b2000000-0000-4000-8000-202712000001', 'month', '2027-12', '2027年12月', 24, 'open', 'a1000000-0000-4000-8000-270204000001', '2026-05-10 15:27:45', '2026-05-10 15:27:45');
INSERT INTO `evaluation_period` VALUES ('f4bea3ad-c3bd-4d14-a8a6-5c6b31b88a61', 'month', '2035-07', 'N', 1, 'draft', NULL, '2026-05-12 01:01:56', '2026-05-12 01:01:56');

-- ----------------------------
-- Table structure for menu
-- ----------------------------
DROP TABLE IF EXISTS `menu`;
CREATE TABLE `menu`  (
  `menu_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `sort_order` int(0) NOT NULL DEFAULT 0,
  PRIMARY KEY (`menu_key`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of menu
-- ----------------------------
INSERT INTO `menu` VALUES ('admin_employees', '员工管理', 8);
INSERT INTO `menu` VALUES ('admin_notifications', '通知管理', 7);
INSERT INTO `menu` VALUES ('admin_permissions', '权限管理', 10);
INSERT INTO `menu` VALUES ('admin_roles', '角色管理', 9);
INSERT INTO `menu` VALUES ('admin_statistics_months', '周期与评选', 11);
INSERT INTO `menu` VALUES ('admin_system_config', '系统配置', 12);
INSERT INTO `menu` VALUES ('admin_templates', '模板管理', 6);
INSERT INTO `menu` VALUES ('home', '工作台', 2);
INSERT INTO `menu` VALUES ('my_performance', '我的绩效', 5);
INSERT INTO `menu` VALUES ('performance_batch_create', '批量创建绩效', 42);
INSERT INTO `menu` VALUES ('performance_export', '导出绩效数据', 4);
INSERT INTO `menu` VALUES ('performance_list', '绩效列表', 3);
INSERT INTO `menu` VALUES ('performance_list_all', '查看全员绩效', 41);
INSERT INTO `menu` VALUES ('performance_review_admin', '绩效终审与校准', 43);
INSERT INTO `menu` VALUES ('todo', '待办', 1);

-- ----------------------------
-- Table structure for notification
-- ----------------------------
DROP TABLE IF EXISTS `notification`;
CREATE TABLE `notification`  (
  `id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `send_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `target_ids` json NOT NULL,
  `sender_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `read_count` int(0) NULL DEFAULT 0,
  `total_count` int(0) NULL DEFAULT 0,
  `_created_at` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0),
  `_created_by` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `_updated_at` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0),
  `_updated_by` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_notification_sender`(`sender_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of notification
-- ----------------------------
INSERT INTO `notification` VALUES ('17eb0afe-3b56-4128-b7ec-a2995a74a0c5', 'Newman通知', '内容', 'specified', '[\"zhou_rong\"]', 'zhou_rong', 0, 1, '2026-05-12 09:23:14', '周荣', '2026-05-12 09:23:14', NULL);
INSERT INTO `notification` VALUES ('3b77a7e1-977f-4976-b83b-3e80eb0e15f1', 'Newman通知', '内容', 'specified', '[\"zhou_rong\"]', 'zhou_rong', 0, 1, '2026-05-12 00:55:31', '周荣', '2026-05-12 00:55:31', NULL);
INSERT INTO `notification` VALUES ('67e1c94c-e44e-48ab-a4f3-5150003cc6a6', 'Newman通知', '内容', 'specified', '[\"zhou_rong\"]', 'zhou_rong', 0, 1, '2026-05-12 01:02:24', '周荣', '2026-05-12 01:02:24', NULL);
INSERT INTO `notification` VALUES ('76278f28-f1f4-4f8b-bb24-a44f9b0a7ae2', 'Newman通知', '内容', 'specified', '[\"zhou_rong\"]', 'zhou_rong', 0, 1, '2026-05-12 00:56:21', '周荣', '2026-05-12 00:56:21', NULL);
INSERT INTO `notification` VALUES ('905c1f4d-4e0d-44b8-b7e9-32725db9b8a6', 'Newman通知', '内容', 'specified', '[\"zhou_rong\"]', 'zhou_rong', 0, 1, '2026-05-12 01:01:46', '周荣', '2026-05-12 01:01:46', NULL);
INSERT INTO `notification` VALUES ('99e63177-dbf9-4e76-8e0b-ef39207de48f', 'Newman通知', '内容', 'specified', '[\"zhou_rong\"]', 'zhou_rong', 0, 1, '2026-05-12 09:20:34', '周荣', '2026-05-12 09:20:34', NULL);
INSERT INTO `notification` VALUES ('9f4274f6-7610-47e4-b8da-dfde44cd96fa', 'Newman通知', '内容', 'specified', '[\"zhou_rong\"]', 'zhou_rong', 0, 1, '2026-05-12 00:58:11', '周荣', '2026-05-12 00:58:11', NULL);
INSERT INTO `notification` VALUES ('af93f547-83cd-45ef-8747-f6b21a77d79f', 'Newman通知', '内容', 'specified', '[\"zhou_rong\"]', 'zhou_rong', 0, 1, '2026-05-12 09:28:02', '周荣', '2026-05-12 09:28:02', NULL);
INSERT INTO `notification` VALUES ('b4b015d8-20d8-4e18-80a2-a28472d82bb8', 'Newman通知', '内容', 'specified', '[\"zhou_rong\"]', 'zhou_rong', 0, 1, '2026-05-12 00:59:53', '周荣', '2026-05-12 00:59:53', NULL);
INSERT INTO `notification` VALUES ('ed9213c1-0365-4573-b028-8afaf5150500', 'Newman通知', '内容', 'specified', '[\"zhou_rong\"]', 'zhou_rong', 0, 1, '2026-05-12 09:21:21', '周荣', '2026-05-12 09:21:21', NULL);

-- ----------------------------
-- Table structure for performance_record
-- ----------------------------
DROP TABLE IF EXISTS `performance_record`;
CREATE TABLE `performance_record`  (
  `id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `employee_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `template_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `period` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'template_selection',
  `manager_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `dotted_manager_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `self_review` json NULL,
  `manager_review` json NULL,
  `dotted_manager_review` json NULL,
  `total_score` decimal(5, 2) NULL DEFAULT NULL,
  `rejection_reason` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
  `_created_at` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0),
  `_created_by` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `_updated_at` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0),
  `_updated_by` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `goal_setting` json NULL,
  `goal_approved_by` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `personal_summary` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
  `final_reviewer_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `final_reviewed_at` datetime(0) NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_performance_record_employee`(`employee_id`) USING BTREE,
  INDEX `idx_performance_record_manager`(`manager_id`) USING BTREE,
  INDEX `idx_performance_record_period`(`period`) USING BTREE,
  INDEX `idx_performance_record_status`(`status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of performance_record
-- ----------------------------
INSERT INTO `performance_record` VALUES ('0544cd33-700e-407c-969f-bd6c5fb4affa', 'demo_emp_01', NULL, '2099-nm-1778548869763', 'template_selection', 'demo_manager', NULL, NULL, NULL, NULL, NULL, NULL, '2026-05-12 09:21:14', NULL, '2026-05-12 09:21:16', NULL, '[{\"target\": \"T1\", \"weight\": 40, \"indicatorName\": \"业绩目标\"}, {\"target\": \"T2\", \"weight\": 30, \"indicatorName\": \"团队协作\"}, {\"target\": \"T3\", \"weight\": 30, \"indicatorName\": \"专业能力\"}]', NULL, NULL, NULL, NULL);
INSERT INTO `performance_record` VALUES ('17626b06-52b2-4e9a-a8b5-735a5b013d53', 'qa_emp_dotted', 'tpl-demo-001', '2099-dt-b-dotted-1778549251644', 'final_review', 'demo_manager', 'demo_dotted_mgr', '[{\"score\": 4, \"comment\": \"ok\", \"indicatorName\": \"业绩目标\"}, {\"score\": 4, \"comment\": \"ok\", \"indicatorName\": \"团队协作\"}, {\"score\": 4, \"comment\": \"ok\", \"indicatorName\": \"专业能力\"}]', '[{\"score\": 4, \"comment\": \"ok\", \"indicatorName\": \"业绩目标\"}, {\"score\": 4, \"comment\": \"ok\", \"indicatorName\": \"团队协作\"}, {\"score\": 4, \"comment\": \"ok\", \"indicatorName\": \"专业能力\"}]', '[{\"score\": 4, \"comment\": \"ok\", \"indicatorName\": \"业绩目标\"}, {\"score\": 4, \"comment\": \"ok\", \"indicatorName\": \"团队协作\"}, {\"score\": 4, \"comment\": \"ok\", \"indicatorName\": \"专业能力\"}]', 4.00, NULL, '2026-05-12 09:27:38', NULL, '2026-05-12 09:27:39', NULL, '[{\"target\": \"T1\", \"weight\": 40, \"indicatorName\": \"业绩目标\"}, {\"target\": \"T2\", \"weight\": 30, \"indicatorName\": \"团队协作\"}, {\"target\": \"T3\", \"weight\": 30, \"indicatorName\": \"专业能力\"}]', 'demo_manager', 'devteam', NULL, NULL);
INSERT INTO `performance_record` VALUES ('1b852f97-ea6d-4d88-9c05-9fb19ab08b24', 'demo_emp_01', NULL, '2099-dt-a1-1778549249432', 'template_selection', 'demo_manager', NULL, NULL, NULL, NULL, NULL, NULL, '2026-05-12 09:27:34', NULL, '2026-05-12 09:27:34', NULL, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `performance_record` VALUES ('1caefc49-96a3-434d-a874-ca833af632d7', 'demo_emp_01', 'tpl-demo-001', '2099-12-newman', 'completed', 'demo_manager', NULL, '[{\"score\": 4, \"comment\": \"n\", \"indicatorName\": \"业绩目标\"}, {\"score\": 4, \"comment\": \"n\", \"indicatorName\": \"团队协作\"}, {\"score\": 4, \"comment\": \"n\", \"indicatorName\": \"专业能力\"}]', '[{\"score\": 4, \"comment\": \"n\", \"indicatorName\": \"业绩目标\"}, {\"score\": 4, \"comment\": \"n\", \"indicatorName\": \"团队协作\"}, {\"score\": 4, \"comment\": \"n\", \"indicatorName\": \"专业能力\"}]', NULL, 4.00, NULL, '2026-05-12 00:55:27', NULL, '2026-05-12 01:02:22', NULL, '[{\"target\": \"T1\", \"weight\": 40, \"indicatorName\": \"业绩目标\"}, {\"target\": \"T2\", \"weight\": 30, \"indicatorName\": \"团队协作\"}, {\"target\": \"T3\", \"weight\": 30, \"indicatorName\": \"专业能力\"}]', 'demo_manager', 'newman', 'zhou_rong', '2026-05-12 01:02:22');
INSERT INTO `performance_record` VALUES ('1d0b6407-e93b-417e-873b-2239ebc6b840', 'demo_emp_01', 'tpl-demo-001', '2099-nm-1778549269585', 'completed', 'demo_manager', NULL, '[{\"score\": 4, \"comment\": \"n\", \"indicatorName\": \"业绩目标\"}, {\"score\": 4, \"comment\": \"n\", \"indicatorName\": \"团队协作\"}, {\"score\": 4, \"comment\": \"n\", \"indicatorName\": \"专业能力\"}]', '[{\"score\": 4, \"comment\": \"n\", \"indicatorName\": \"业绩目标\"}, {\"score\": 4, \"comment\": \"n\", \"indicatorName\": \"团队协作\"}, {\"score\": 4, \"comment\": \"n\", \"indicatorName\": \"专业能力\"}]', NULL, 4.20, NULL, '2026-05-12 09:27:54', NULL, '2026-05-12 09:27:59', NULL, '[{\"target\": \"T1\", \"weight\": 40, \"indicatorName\": \"业绩目标\"}, {\"target\": \"T2\", \"weight\": 30, \"indicatorName\": \"团队协作\"}, {\"target\": \"T3\", \"weight\": 30, \"indicatorName\": \"专业能力\"}]', 'demo_manager', 'newman', 'zhou_rong', '2026-05-12 09:27:59');
INSERT INTO `performance_record` VALUES ('26abb449-3218-416a-b5c9-c7670dbcf637', 'ou_547db352b2a62656356069864b0258a4', NULL, '2026-08', 'template_selection', 'ou_9ed535ad58bbbc93af5135824859c765', NULL, NULL, NULL, NULL, NULL, NULL, '2026-05-11 11:35:44', NULL, '2026-05-11 11:35:44', NULL, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `performance_record` VALUES ('3b9fe431-6b01-482b-b7f3-5e718b113578', 'demo_emp_01', NULL, '2099-dt-a1-1778549233147', 'template_selection', 'demo_manager', NULL, NULL, NULL, NULL, NULL, NULL, '2026-05-12 09:27:18', NULL, '2026-05-12 09:27:18', NULL, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `performance_record` VALUES ('45c5ac79-c9c0-4e28-99ce-8759e63b679d', 'demo_emp_01', 'tpl-demo-001', '2099-nm-1778548942511', 'goal_setting', 'demo_manager', NULL, NULL, NULL, NULL, NULL, NULL, '2026-05-12 09:22:27', NULL, '2026-05-12 09:22:31', NULL, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `performance_record` VALUES ('4bfe3682-1a5e-459a-826a-8a1f4973cc22', 'ou_9ed535ad58bbbc93af5135824859c765', NULL, '2026-08', 'template_selection', 'ou_1edb87554c088bae740b33ed93fc3a0a', NULL, NULL, NULL, NULL, NULL, NULL, '2026-05-11 11:35:44', NULL, '2026-05-11 11:35:44', NULL, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `performance_record` VALUES ('56ff033b-887b-4e1b-9d9b-ec42eaeaf344', 'ou_4c11e99fb804eed678c04c5cd74e9ca8', NULL, '2026-08', 'template_selection', 'ou_1edb87554c088bae740b33ed93fc3a0a', NULL, NULL, NULL, NULL, NULL, NULL, '2026-05-11 11:35:44', NULL, '2026-05-11 11:35:44', NULL, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `performance_record` VALUES ('5dcf810b-c587-4e8b-906a-ca9a193c85a4', 'demo_emp_02', NULL, '2099-dt-a2-1778549233147', 'template_selection', 'demo_manager', NULL, NULL, NULL, NULL, NULL, NULL, '2026-05-12 09:27:18', NULL, '2026-05-12 09:27:18', NULL, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `performance_record` VALUES ('5fc89062-c16b-47f8-a522-a5a31f73fc4e', 'demo_emp_02', 'tpl-demo-001', '2099-dt-b-reject-1778549251644', 'goal_pending_review', 'demo_manager', NULL, NULL, NULL, NULL, NULL, 'devteam', '2026-05-12 09:27:39', NULL, '2026-05-12 09:27:39', NULL, '[{\"target\": \"T1\", \"weight\": 40, \"indicatorName\": \"业绩目标\"}, {\"target\": \"T2\", \"weight\": 30, \"indicatorName\": \"团队协作\"}, {\"target\": \"T3\", \"weight\": 30, \"indicatorName\": \"专业能力\"}]', 'demo_manager', NULL, NULL, NULL);
INSERT INTO `performance_record` VALUES ('758b579b-12c1-4e71-8d4a-aac4d1a72dcc', 'demo_emp_02', NULL, '2099-dt-a2-1778549249432', 'template_selection', 'demo_manager', NULL, NULL, NULL, NULL, NULL, NULL, '2026-05-12 09:27:34', NULL, '2026-05-12 09:27:34', NULL, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `performance_record` VALUES ('783a07ce-967c-4462-8c70-156825da08f5', 'demo_emp_01', 'tpl-demo-001', '2099-dt-b-linear-1778549251644', 'completed', 'demo_manager', NULL, '[{\"score\": 4, \"comment\": \"ok\", \"indicatorName\": \"业绩目标\"}, {\"score\": 4, \"comment\": \"ok\", \"indicatorName\": \"团队协作\"}, {\"score\": 4, \"comment\": \"ok\", \"indicatorName\": \"专业能力\"}]', '[{\"score\": 4, \"comment\": \"ok\", \"indicatorName\": \"业绩目标\"}, {\"score\": 4, \"comment\": \"ok\", \"indicatorName\": \"团队协作\"}, {\"score\": 4, \"comment\": \"ok\", \"indicatorName\": \"专业能力\"}]', NULL, 4.20, NULL, '2026-05-12 09:27:36', NULL, '2026-05-12 09:27:37', NULL, '[{\"target\": \"T1\", \"weight\": 40, \"indicatorName\": \"业绩目标\"}, {\"target\": \"T2\", \"weight\": 30, \"indicatorName\": \"团队协作\"}, {\"target\": \"T3\", \"weight\": 30, \"indicatorName\": \"专业能力\"}]', 'demo_manager', 'devteam', 'zhou_rong', '2026-05-12 09:27:37');
INSERT INTO `performance_record` VALUES ('79aa10a7-562b-40bc-aa20-8822e729b87f', 'ou_163f7854da693143b952ff3e9c4975a2', NULL, '2026-08', 'template_selection', 'ou_1edb87554c088bae740b33ed93fc3a0a', NULL, NULL, NULL, NULL, NULL, NULL, '2026-05-11 11:35:44', NULL, '2026-05-11 11:35:44', NULL, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `performance_record` VALUES ('7d6db4ef-a03a-4216-acee-06253e752b76', 'zhou_rong', 'tpl-demo-001', '2026-04', 'completed', 'demo_manager', 'demo_emp_02', '[{\"score\": 100, \"comment\": \"满足整体\", \"indicatorName\": \"业绩目标\"}, {\"score\": 100, \"comment\": \"测试\", \"indicatorName\": \"团队协作\"}, {\"score\": 100, \"comment\": \"测试\", \"indicatorName\": \"专业能力\"}]', '[{\"score\": 100, \"comment\": \"10\", \"indicatorName\": \"业绩目标\"}, {\"score\": 85, \"comment\": \"100\", \"indicatorName\": \"团队协作\"}, {\"score\": 50, \"comment\": \"100\", \"indicatorName\": \"专业能力\"}]', '[{\"score\": 100, \"comment\": \"测试\", \"indicatorName\": \"业绩目标\"}, {\"score\": 60, \"comment\": \"测试\", \"indicatorName\": \"团队协作\"}, {\"score\": 100, \"comment\": \"测试\", \"indicatorName\": \"专业能力\"}]', 82.75, NULL, '2026-05-10 23:41:28', NULL, '2026-05-11 00:18:02', NULL, '[{\"weight\": 40, \"indicatorName\": \"业绩目标\"}, {\"weight\": 30, \"indicatorName\": \"团队协作\"}, {\"weight\": 30, \"indicatorName\": \"专业能力\"}]', 'demo_emp_02', '测试', 'zhou_rong', '2026-05-10 16:18:00');
INSERT INTO `performance_record` VALUES ('83a83fa8-04de-4236-b582-35c6783d7e77', 'ou_1edb87554c088bae740b33ed93fc3a0a', NULL, '2026-08', 'template_selection', 'ou_de36396e25ed1528abf0c035aa098aab', NULL, NULL, NULL, NULL, NULL, NULL, '2026-05-11 11:35:44', NULL, '2026-05-11 11:35:44', NULL, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `performance_record` VALUES ('88379910-fd16-44e9-a264-143641598091', 'zhou_rong', 'tpl-demo-001', '2025-Q3', 'final_review', 'demo_manager', NULL, '[{\"score\": 100, \"comment\": \"第一\", \"indicatorName\": \"业绩目标\"}, {\"score\": 100, \"comment\": \"测试\", \"indicatorName\": \"团队协作\"}, {\"score\": 100, \"comment\": \"你好\", \"indicatorName\": \"专业能力\"}]', '[{\"score\": 100, \"comment\": \"良好\", \"indicatorName\": \"业绩目标\"}, {\"score\": 50, \"comment\": \"测试\", \"indicatorName\": \"团队协作\"}, {\"score\": 56, \"comment\": \"可以\", \"indicatorName\": \"专业能力\"}]', NULL, 71.80, NULL, '2026-05-10 15:30:22', NULL, '2026-05-11 00:12:15', NULL, '[{\"weight\": 40, \"indicatorName\": \"业绩目标\"}, {\"weight\": 30, \"indicatorName\": \"团队协作\"}, {\"weight\": 30, \"indicatorName\": \"专业能力\"}]', 'demo_manager', '总结', NULL, NULL);
INSERT INTO `performance_record` VALUES ('8cf068a1-8d52-47e1-b46a-9dc1e1db7582', 'zhou_rong', 'tpl-demo-001', '2025-Q4', 'manager_review', 'demo_manager', NULL, '[{\"score\": 100, \"comment\": \"\", \"indicatorName\": \"业绩目标\"}, {\"score\": 100, \"comment\": \"\", \"indicatorName\": \"团队协作\"}, {\"score\": 100, \"comment\": \"\", \"indicatorName\": \"专业能力\"}]', NULL, NULL, NULL, NULL, '2026-05-10 12:18:40', NULL, '2026-05-10 12:41:10', NULL, '[{\"target\": \"100\", \"weight\": 40, \"indicatorName\": \"业绩目标\"}, {\"target\": \"100\", \"weight\": 30, \"indicatorName\": \"团队协作\"}, {\"target\": \"100\", \"weight\": 30, \"indicatorName\": \"专业能力\"}]', 'demo_manager', NULL, NULL, NULL);
INSERT INTO `performance_record` VALUES ('9914e1f2-9d06-4c87-9fee-c009194f16b4', 'qa_emp_dotted', NULL, '2099-dt-b-dotted-1778549235291', 'template_selection', 'demo_manager', 'demo_dotted_mgr', NULL, NULL, NULL, NULL, NULL, '2026-05-12 09:27:20', NULL, '2026-05-12 09:27:20', NULL, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `performance_record` VALUES ('a7cd84fd-a850-4b0f-870c-5aad10372bc1', 'demo_emp_01', NULL, '2099-dt-b-linear-1778549235291', 'template_selection', 'demo_manager', NULL, NULL, NULL, NULL, NULL, NULL, '2026-05-12 09:27:20', NULL, '2026-05-12 09:27:20', NULL, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `performance_record` VALUES ('d0f45088-433d-4198-bb3b-e5bdb2797d37', 'demo_emp_01', 'tpl-demo-001', '2099-nm-1778548982673', 'completed', 'demo_manager', NULL, '[{\"score\": 4, \"comment\": \"n\", \"indicatorName\": \"业绩目标\"}, {\"score\": 4, \"comment\": \"n\", \"indicatorName\": \"团队协作\"}, {\"score\": 4, \"comment\": \"n\", \"indicatorName\": \"专业能力\"}]', '[{\"score\": 4, \"comment\": \"n\", \"indicatorName\": \"业绩目标\"}, {\"score\": 4, \"comment\": \"n\", \"indicatorName\": \"团队协作\"}, {\"score\": 4, \"comment\": \"n\", \"indicatorName\": \"专业能力\"}]', NULL, 4.20, NULL, '2026-05-12 09:23:07', NULL, '2026-05-12 09:23:12', NULL, '[{\"target\": \"T1\", \"weight\": 40, \"indicatorName\": \"业绩目标\"}, {\"target\": \"T2\", \"weight\": 30, \"indicatorName\": \"团队协作\"}, {\"target\": \"T3\", \"weight\": 30, \"indicatorName\": \"专业能力\"}]', 'demo_manager', 'newman', 'zhou_rong', '2026-05-12 09:23:12');
INSERT INTO `performance_record` VALUES ('d115a0e9-66f6-4628-b155-8994dc94d8e0', 'demo_emp_02', NULL, '2099-dt-b-reject-1778549235291', 'template_selection', 'demo_manager', NULL, NULL, NULL, NULL, NULL, NULL, '2026-05-12 09:27:21', NULL, '2026-05-12 09:27:21', NULL, NULL, NULL, NULL, NULL, NULL);
INSERT INTO `performance_record` VALUES ('f81dd025-6b82-4737-8676-6b5aef8fb945', 'demo_emp_01', 'tpl-demo-001', '2099-nm-1778548822088', 'completed', 'demo_manager', NULL, '[{\"score\": 4, \"comment\": \"n\", \"indicatorName\": \"业绩目标\"}, {\"score\": 4, \"comment\": \"n\", \"indicatorName\": \"团队协作\"}, {\"score\": 4, \"comment\": \"n\", \"indicatorName\": \"专业能力\"}]', '[{\"score\": 4, \"comment\": \"n\", \"indicatorName\": \"业绩目标\"}, {\"score\": 4, \"comment\": \"n\", \"indicatorName\": \"团队协作\"}, {\"score\": 4, \"comment\": \"n\", \"indicatorName\": \"专业能力\"}]', NULL, 4.00, NULL, '2026-05-12 09:20:27', NULL, '2026-05-12 09:20:31', NULL, '[{\"target\": \"T1\", \"weight\": 40, \"indicatorName\": \"业绩目标\"}, {\"target\": \"T2\", \"weight\": 30, \"indicatorName\": \"团队协作\"}, {\"target\": \"T3\", \"weight\": 30, \"indicatorName\": \"专业能力\"}]', 'demo_manager', 'newman', 'zhou_rong', '2026-05-12 09:20:31');
INSERT INTO `performance_record` VALUES ('fc3d8675-8354-4da9-89d5-39f5ea5e9db2', 'zhou_rong', 'tpl-demo-001', '2026-Q2', 'completed', 'demo_manager', NULL, '[{\"score\": 85, \"comment\": \"自评：业绩目标 完成良好\", \"indicatorName\": \"业绩目标\"}, {\"score\": 85, \"comment\": \"自评：团队协作 完成良好\", \"indicatorName\": \"团队协作\"}, {\"score\": 85, \"comment\": \"自评：专业能力 完成良好\", \"indicatorName\": \"专业能力\"}]', '[{\"score\": 90, \"comment\": \"业绩突出\", \"indicatorName\": \"业绩目标\"}, {\"score\": 85, \"comment\": \"协作良好\", \"indicatorName\": \"团队协作\"}, {\"score\": 80, \"comment\": \"技能扎实\", \"indicatorName\": \"专业能力\"}]', NULL, 85.50, NULL, '2026-05-10 12:12:48', NULL, '2026-05-10 12:12:49', NULL, '[{\"target\": \"完成 业绩目标 相关目标\", \"weight\": 40, \"indicatorName\": \"业绩目标\"}, {\"target\": \"完成 团队协作 相关目标\", \"weight\": 30, \"indicatorName\": \"团队协作\"}, {\"target\": \"完成 专业能力 相关目标\", \"weight\": 30, \"indicatorName\": \"专业能力\"}]', 'demo_manager', NULL, 'zhou_rong', '2026-05-10 04:12:44');

-- ----------------------------
-- Table structure for performance_template
-- ----------------------------
DROP TABLE IF EXISTS `performance_template`;
CREATE TABLE `performance_template`  (
  `id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `position` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `indicators` json NOT NULL,
  `status` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'enabled',
  `version` int(0) NULL DEFAULT 1,
  `_created_at` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0),
  `_created_by` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `_updated_at` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0),
  `_updated_by` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_performance_template_status`(`status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of performance_template
-- ----------------------------
INSERT INTO `performance_template` VALUES ('11884689-a188-4983-97f0-a9a5ecc6830a', '通用绩效考核模板 (副本)', '员工', '[{\"name\": \"业绩目标\", \"weight\": 40, \"description\": \"核心业务指标完成情况\"}, {\"name\": \"团队协作\", \"weight\": 30, \"description\": \"跨部门沟通与协作能力\"}, {\"name\": \"专业能力\", \"weight\": 30, \"description\": \"岗位技能与学习成长\"}]', 'disabled', 1, '2026-05-12 00:56:20', NULL, '2026-05-12 00:56:20', NULL);
INSERT INTO `performance_template` VALUES ('18774b5d-ff62-4eb0-b053-6d292626ec22', '通用绩效考核模板 (副本)', '员工', '[{\"name\": \"业绩目标\", \"weight\": 40, \"description\": \"核心业务指标完成情况\"}, {\"name\": \"团队协作\", \"weight\": 30, \"description\": \"跨部门沟通与协作能力\"}, {\"name\": \"专业能力\", \"weight\": 30, \"description\": \"岗位技能与学习成长\"}]', 'disabled', 1, '2026-05-12 00:55:30', NULL, '2026-05-12 00:55:30', NULL);
INSERT INTO `performance_template` VALUES ('2a70d854-9d4b-4f36-b553-b51d09de03fa', 'Newman模板', '员工', '[{\"name\": \"指标A\", \"weight\": 50, \"description\": \"d\"}, {\"name\": \"指标B\", \"weight\": 50, \"description\": \"d\"}]', 'enabled', 1, '2026-05-12 01:01:44', 'zhou_rong', '2026-05-12 01:01:44', 'zhou_rong');
INSERT INTO `performance_template` VALUES ('2df9f8d6-ce83-45c6-9529-2b8686854ab5', 'Newman模板', '员工', '[{\"name\": \"指标A\", \"weight\": 50, \"description\": \"d\"}, {\"name\": \"指标B\", \"weight\": 50, \"description\": \"d\"}]', 'enabled', 1, '2026-05-12 00:58:09', 'zhou_rong', '2026-05-12 00:58:09', 'zhou_rong');
INSERT INTO `performance_template` VALUES ('391b3ae5-85cb-4761-8793-864c53d4d70e', 'Newman模板', '员工', '[{\"name\": \"指标A\", \"weight\": 50, \"description\": \"d\"}, {\"name\": \"指标B\", \"weight\": 50, \"description\": \"d\"}]', 'enabled', 1, '2026-05-12 09:20:32', 'zhou_rong', '2026-05-12 09:20:32', 'zhou_rong');
INSERT INTO `performance_template` VALUES ('415b3c49-5d8e-41b8-8398-d534f4ba0955', '通用绩效考核模板 (副本)', '员工', '[{\"name\": \"业绩目标\", \"weight\": 40, \"description\": \"核心业务指标完成情况\"}, {\"name\": \"团队协作\", \"weight\": 30, \"description\": \"跨部门沟通与协作能力\"}, {\"name\": \"专业能力\", \"weight\": 30, \"description\": \"岗位技能与学习成长\"}]', 'disabled', 1, '2026-05-12 00:58:10', NULL, '2026-05-12 00:58:10', NULL);
INSERT INTO `performance_template` VALUES ('55a6f530-4118-4e71-a0d5-a5382faee434', 'Newman模板', '员工', '[{\"name\": \"指标A\", \"weight\": 50, \"description\": \"d\"}, {\"name\": \"指标B\", \"weight\": 50, \"description\": \"d\"}]', 'enabled', 1, '2026-05-12 00:56:20', 'zhou_rong', '2026-05-12 00:56:20', 'zhou_rong');
INSERT INTO `performance_template` VALUES ('73f43acd-04ec-4f58-9526-640ee09c80ec', '通用绩效考核模板 (副本)', '员工', '[{\"name\": \"业绩目标\", \"weight\": 40, \"description\": \"核心业务指标完成情况\"}, {\"name\": \"团队协作\", \"weight\": 30, \"description\": \"跨部门沟通与协作能力\"}, {\"name\": \"专业能力\", \"weight\": 30, \"description\": \"岗位技能与学习成长\"}]', 'disabled', 1, '2026-05-12 09:23:14', NULL, '2026-05-12 09:23:14', NULL);
INSERT INTO `performance_template` VALUES ('80c1d085-1ad5-464e-bc20-6dee1ec9df9a', 'Newman模板', '员工', '[{\"name\": \"指标A\", \"weight\": 50, \"description\": \"d\"}, {\"name\": \"指标B\", \"weight\": 50, \"description\": \"d\"}]', 'enabled', 1, '2026-05-12 00:59:51', 'zhou_rong', '2026-05-12 00:59:51', 'zhou_rong');
INSERT INTO `performance_template` VALUES ('8eae3d22-3431-4bdb-ad61-aa585e138fe7', '通用绩效考核模板 (副本)', '员工', '[{\"name\": \"业绩目标\", \"weight\": 40, \"description\": \"核心业务指标完成情况\"}, {\"name\": \"团队协作\", \"weight\": 30, \"description\": \"跨部门沟通与协作能力\"}, {\"name\": \"专业能力\", \"weight\": 30, \"description\": \"岗位技能与学习成长\"}]', 'disabled', 1, '2026-05-12 01:02:24', NULL, '2026-05-12 01:02:24', NULL);
INSERT INTO `performance_template` VALUES ('8eb288bd-c7bd-41b2-a000-62dd2dd12fc9', 'Newman模板', '员工', '[{\"name\": \"指标A\", \"weight\": 50, \"description\": \"d\"}, {\"name\": \"指标B\", \"weight\": 50, \"description\": \"d\"}]', 'enabled', 1, '2026-05-12 09:21:20', 'zhou_rong', '2026-05-12 09:21:20', 'zhou_rong');
INSERT INTO `performance_template` VALUES ('914840e6-8c67-413b-a3d1-1ba8c13d468b', '通用绩效考核模板 (副本)', '员工', '[{\"name\": \"业绩目标\", \"weight\": 40, \"description\": \"核心业务指标完成情况\"}, {\"name\": \"团队协作\", \"weight\": 30, \"description\": \"跨部门沟通与协作能力\"}, {\"name\": \"专业能力\", \"weight\": 30, \"description\": \"岗位技能与学习成长\"}]', 'disabled', 1, '2026-05-12 09:21:20', NULL, '2026-05-12 09:21:20', NULL);
INSERT INTO `performance_template` VALUES ('9af93b35-0fab-47cf-8529-7656f1e75f63', '通用绩效考核模板 (副本)', '员工', '[{\"name\": \"业绩目标\", \"weight\": 40, \"description\": \"核心业务指标完成情况\"}, {\"name\": \"团队协作\", \"weight\": 30, \"description\": \"跨部门沟通与协作能力\"}, {\"name\": \"专业能力\", \"weight\": 30, \"description\": \"岗位技能与学习成长\"}]', 'disabled', 1, '2026-05-12 09:28:01', NULL, '2026-05-12 09:28:01', NULL);
INSERT INTO `performance_template` VALUES ('9bbbf589-cbf8-4b67-aa5e-c6ab5ce51897', 'Newman模板', '员工', '[{\"name\": \"指标A\", \"weight\": 50, \"description\": \"d\"}, {\"name\": \"指标B\", \"weight\": 50, \"description\": \"d\"}]', 'enabled', 1, '2026-05-12 09:23:13', 'zhou_rong', '2026-05-12 09:23:13', 'zhou_rong');
INSERT INTO `performance_template` VALUES ('c16e25a8-5f84-4d2b-8981-0273ebaf641d', '通用绩效考核模板 (副本)', '员工', '[{\"name\": \"业绩目标\", \"weight\": 40, \"description\": \"核心业务指标完成情况\"}, {\"name\": \"团队协作\", \"weight\": 30, \"description\": \"跨部门沟通与协作能力\"}, {\"name\": \"专业能力\", \"weight\": 30, \"description\": \"岗位技能与学习成长\"}]', 'disabled', 1, '2026-05-12 00:59:52', NULL, '2026-05-12 00:59:52', NULL);
INSERT INTO `performance_template` VALUES ('c67e99c7-33f1-4817-a691-1746e8b6a9c1', 'Newman模板', '员工', '[{\"name\": \"指标A\", \"weight\": 50, \"description\": \"d\"}, {\"name\": \"指标B\", \"weight\": 50, \"description\": \"d\"}]', 'enabled', 1, '2026-05-12 00:55:30', 'zhou_rong', '2026-05-12 00:55:30', 'zhou_rong');
INSERT INTO `performance_template` VALUES ('c9f241fe-1fff-447d-a0e8-a2aa083230f8', 'Newman模板', '员工', '[{\"name\": \"指标A\", \"weight\": 50, \"description\": \"d\"}, {\"name\": \"指标B\", \"weight\": 50, \"description\": \"d\"}]', 'enabled', 1, '2026-05-12 09:28:00', 'zhou_rong', '2026-05-12 09:28:00', 'zhou_rong');
INSERT INTO `performance_template` VALUES ('ce85e48d-857a-49d6-ba35-056536f70b88', 'Newman模板', '员工', '[{\"name\": \"指标A\", \"weight\": 50, \"description\": \"d\"}, {\"name\": \"指标B\", \"weight\": 50, \"description\": \"d\"}]', 'enabled', 1, '2026-05-12 01:02:23', 'zhou_rong', '2026-05-12 01:02:23', 'zhou_rong');
INSERT INTO `performance_template` VALUES ('f1fc1d2c-5ef8-450b-8ad3-a6adba838692', '通用绩效考核模板 (副本)', '员工', '[{\"name\": \"业绩目标\", \"weight\": 40, \"description\": \"核心业务指标完成情况\"}, {\"name\": \"团队协作\", \"weight\": 30, \"description\": \"跨部门沟通与协作能力\"}, {\"name\": \"专业能力\", \"weight\": 30, \"description\": \"岗位技能与学习成长\"}]', 'disabled', 1, '2026-05-12 09:20:33', NULL, '2026-05-12 09:20:33', NULL);
INSERT INTO `performance_template` VALUES ('fde1e9a3-d9c3-4f91-9c0b-7eba6ebf1d7a', '通用绩效考核模板 (副本)', '员工', '[{\"name\": \"业绩目标\", \"weight\": 40, \"description\": \"核心业务指标完成情况\"}, {\"name\": \"团队协作\", \"weight\": 30, \"description\": \"跨部门沟通与协作能力\"}, {\"name\": \"专业能力\", \"weight\": 30, \"description\": \"岗位技能与学习成长\"}]', 'disabled', 1, '2026-05-12 01:01:45', NULL, '2026-05-12 01:01:45', NULL);
INSERT INTO `performance_template` VALUES ('tpl-demo-001', '通用绩效考核模板', '员工', '[{\"name\": \"业绩目标\", \"weight\": 40, \"description\": \"核心业务指标完成情况\"}, {\"name\": \"团队协作\", \"weight\": 30, \"description\": \"跨部门沟通与协作能力\"}, {\"name\": \"专业能力\", \"weight\": 30, \"description\": \"岗位技能与学习成长\"}]', 'enabled', 1, '2026-05-10 12:07:34', NULL, '2026-05-12 09:28:01', NULL);

-- ----------------------------
-- Table structure for period_award
-- ----------------------------
DROP TABLE IF EXISTS `period_award`;
CREATE TABLE `period_award`  (
  `id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `period_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `award_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `employee_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `performance_record_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `remark` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
  `created_by` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `_created_at` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0),
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uq_period_award_period_award_emp`(`period_id`, `award_code`, `employee_id`) USING BTREE,
  INDEX `idx_period_award_period`(`period_id`) USING BTREE,
  INDEX `fk_period_award_award`(`award_code`) USING BTREE,
  CONSTRAINT `fk_period_award_award` FOREIGN KEY (`award_code`) REFERENCES `award_type` (`code`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_period_award_period` FOREIGN KEY (`period_id`) REFERENCES `evaluation_period` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of period_award
-- ----------------------------
INSERT INTO `period_award` VALUES ('6b723a77-5e4c-4af0-9ec5-380a1cbbf8d0', 'a1000000-0000-4000-8000-260202000001', 'quarter_star', 'zhou_rong', 'fc3d8675-8354-4da9-89d5-39f5ea5e9db2', NULL, 'zhou_rong', '2026-05-10 15:53:22');
INSERT INTO `period_award` VALUES ('c3df3d91-021f-4d69-a2d2-cf0abacd3251', 'a1000000-0000-4000-8000-260202000001', 'altruism', 'demo_manager', NULL, NULL, 'zhou_rong', '2026-05-10 15:53:46');

-- ----------------------------
-- Table structure for role
-- ----------------------------
DROP TABLE IF EXISTS `role`;
CREATE TABLE `role`  (
  `role_key` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `is_system` tinyint(1) NOT NULL DEFAULT 1,
  `sort_order` int(0) NOT NULL DEFAULT 0,
  PRIMARY KEY (`role_key`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of role
-- ----------------------------
INSERT INTO `role` VALUES ('admin', '管理员', 1, 2);
INSERT INTO `role` VALUES ('ceshi', '测试', 0, 0);
INSERT INTO `role` VALUES ('employee', '员工', 1, 1);
INSERT INTO `role` VALUES ('super_admin', '超级管理员', 1, 3);

-- ----------------------------
-- Table structure for role_menu
-- ----------------------------
DROP TABLE IF EXISTS `role_menu`;
CREATE TABLE `role_menu`  (
  `role_key` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `menu_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `allowed` tinyint(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (`role_key`, `menu_key`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of role_menu
-- ----------------------------
INSERT INTO `role_menu` VALUES ('admin', 'admin_employees', 1);
INSERT INTO `role_menu` VALUES ('admin', 'admin_notifications', 1);
INSERT INTO `role_menu` VALUES ('admin', 'admin_permissions', 0);
INSERT INTO `role_menu` VALUES ('admin', 'admin_roles', 1);
INSERT INTO `role_menu` VALUES ('admin', 'admin_statistics_months', 1);
INSERT INTO `role_menu` VALUES ('admin', 'admin_system_config', 1);
INSERT INTO `role_menu` VALUES ('admin', 'admin_templates', 1);
INSERT INTO `role_menu` VALUES ('admin', 'home', 1);
INSERT INTO `role_menu` VALUES ('admin', 'my_performance', 1);
INSERT INTO `role_menu` VALUES ('admin', 'performance_batch_create', 0);
INSERT INTO `role_menu` VALUES ('admin', 'performance_export', 0);
INSERT INTO `role_menu` VALUES ('admin', 'performance_list', 1);
INSERT INTO `role_menu` VALUES ('admin', 'performance_list_all', 1);
INSERT INTO `role_menu` VALUES ('admin', 'performance_review_admin', 1);
INSERT INTO `role_menu` VALUES ('admin', 'todo', 0);
INSERT INTO `role_menu` VALUES ('ceshi', 'admin_employees', 0);
INSERT INTO `role_menu` VALUES ('ceshi', 'admin_notifications', 0);
INSERT INTO `role_menu` VALUES ('ceshi', 'admin_performance_calibration', 0);
INSERT INTO `role_menu` VALUES ('ceshi', 'admin_permissions', 0);
INSERT INTO `role_menu` VALUES ('ceshi', 'admin_roles', 0);
INSERT INTO `role_menu` VALUES ('ceshi', 'admin_statistics_months', 0);
INSERT INTO `role_menu` VALUES ('ceshi', 'admin_system_config', 0);
INSERT INTO `role_menu` VALUES ('ceshi', 'admin_templates', 0);
INSERT INTO `role_menu` VALUES ('ceshi', 'home', 0);
INSERT INTO `role_menu` VALUES ('ceshi', 'my_performance', 0);
INSERT INTO `role_menu` VALUES ('ceshi', 'performance_batch_create', 0);
INSERT INTO `role_menu` VALUES ('ceshi', 'performance_export', 0);
INSERT INTO `role_menu` VALUES ('ceshi', 'performance_list', 0);
INSERT INTO `role_menu` VALUES ('ceshi', 'performance_list_all', 0);
INSERT INTO `role_menu` VALUES ('ceshi', 'performance_review_admin', 0);
INSERT INTO `role_menu` VALUES ('ceshi', 'todo', 0);
INSERT INTO `role_menu` VALUES ('employee', 'admin_employees', 0);
INSERT INTO `role_menu` VALUES ('employee', 'admin_notifications', 0);
INSERT INTO `role_menu` VALUES ('employee', 'admin_permissions', 0);
INSERT INTO `role_menu` VALUES ('employee', 'admin_roles', 0);
INSERT INTO `role_menu` VALUES ('employee', 'admin_statistics_months', 0);
INSERT INTO `role_menu` VALUES ('employee', 'admin_system_config', 0);
INSERT INTO `role_menu` VALUES ('employee', 'admin_templates', 0);
INSERT INTO `role_menu` VALUES ('employee', 'home', 0);
INSERT INTO `role_menu` VALUES ('employee', 'my_performance', 1);
INSERT INTO `role_menu` VALUES ('employee', 'performance_batch_create', 0);
INSERT INTO `role_menu` VALUES ('employee', 'performance_export', 0);
INSERT INTO `role_menu` VALUES ('employee', 'performance_list', 1);
INSERT INTO `role_menu` VALUES ('employee', 'performance_list_all', 0);
INSERT INTO `role_menu` VALUES ('employee', 'performance_review_admin', 0);
INSERT INTO `role_menu` VALUES ('employee', 'todo', 1);
INSERT INTO `role_menu` VALUES ('super_admin', 'admin_employees', 1);
INSERT INTO `role_menu` VALUES ('super_admin', 'admin_notifications', 1);
INSERT INTO `role_menu` VALUES ('super_admin', 'admin_permissions', 1);
INSERT INTO `role_menu` VALUES ('super_admin', 'admin_roles', 1);
INSERT INTO `role_menu` VALUES ('super_admin', 'admin_statistics_months', 1);
INSERT INTO `role_menu` VALUES ('super_admin', 'admin_system_config', 1);
INSERT INTO `role_menu` VALUES ('super_admin', 'admin_templates', 1);
INSERT INTO `role_menu` VALUES ('super_admin', 'home', 1);
INSERT INTO `role_menu` VALUES ('super_admin', 'my_performance', 1);
INSERT INTO `role_menu` VALUES ('super_admin', 'performance_batch_create', 1);
INSERT INTO `role_menu` VALUES ('super_admin', 'performance_export', 1);
INSERT INTO `role_menu` VALUES ('super_admin', 'performance_list', 1);
INSERT INTO `role_menu` VALUES ('super_admin', 'performance_list_all', 1);
INSERT INTO `role_menu` VALUES ('super_admin', 'performance_review_admin', 1);
INSERT INTO `role_menu` VALUES ('super_admin', 'todo', 1);

-- ----------------------------
-- Table structure for role_menu_permission
-- ----------------------------
DROP TABLE IF EXISTS `role_menu_permission`;
CREATE TABLE `role_menu_permission`  (
  `role` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `menu_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `allowed` tinyint(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (`role`, `menu_key`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of role_menu_permission
-- ----------------------------
INSERT INTO `role_menu_permission` VALUES ('employee', 'home', 0);

-- ----------------------------
-- Table structure for system_config
-- ----------------------------
DROP TABLE IF EXISTS `system_config`;
CREATE TABLE `system_config`  (
  `config_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `config_value` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `_updated_at` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0),
  PRIMARY KEY (`config_key`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of system_config
-- ----------------------------
INSERT INTO `system_config` VALUES ('dotted_manager_review_weight', '0.3', '2026-05-10 12:07:34');
INSERT INTO `system_config` VALUES ('feishu_app_id', 'cli_a95e20834da35ccb', '2026-05-10 11:34:37');
INSERT INTO `system_config` VALUES ('feishu_app_secret', 'E9LL9YoJA988T2eB8XEcjgBh5ZOpD3sV', '2026-05-10 11:34:37');
INSERT INTO `system_config` VALUES ('manager_review_weight', '0.7', '2026-05-10 12:07:34');

-- ----------------------------
-- Table structure for user_role
-- ----------------------------
DROP TABLE IF EXISTS `user_role`;
CREATE TABLE `user_role`  (
  `user_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `role_key` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`user_id`, `role_key`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of user_role
-- ----------------------------
INSERT INTO `user_role` VALUES ('demo_emp_01', 'employee');
INSERT INTO `user_role` VALUES ('demo_emp_02', 'employee');
INSERT INTO `user_role` VALUES ('qa_admin', 'admin');
INSERT INTO `user_role` VALUES ('zhou_rong', 'super_admin');

-- ----------------------------
-- Procedure structure for clear_all_tables
-- ----------------------------
DROP PROCEDURE IF EXISTS `clear_all_tables`;
delimiter ;;
CREATE PROCEDURE `clear_all_tables`()
BEGIN
  DECLARE table_name VARCHAR(255);
  DECLARE done INT DEFAULT FALSE;
  DECLARE table_cursor CURSOR FOR SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE();
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = TRUE;

  OPEN table_cursor;

  table_loop: LOOP
    FETCH table_cursor INTO table_name;
    IF done THEN
      LEAVE table_loop;
    END IF;
    SET @sql = CONCAT('TRUNCATE TABLE ', table_name);
    PREPARE stmt FROM @sql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END LOOP;

  CLOSE table_cursor;
END
;;
delimiter ;

SET FOREIGN_KEY_CHECKS = 1;
