/*
 Navicat Premium Data Transfer

 Source Server         : 172.25.1.43
 Source Server Type    : MySQL
 Source Server Version : 80045
 Source Host           : 172.25.1.43:3306
 Source Schema         : jixiao

 Target Server Type    : MySQL
 Target Server Version : 80045
 File Encoding         : 65001

 Date: 13/05/2026 14:49:50
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for feishu_subject
-- ----------------------------
DROP TABLE IF EXISTS `feishu_subject`;
CREATE TABLE `feishu_subject`  (
  `id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `sort_order` int(0) NOT NULL DEFAULT 0,
  `enabled` tinyint(1) NOT NULL DEFAULT 1,
  `notify_frontend_base_url` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `notify_feishu_web_app_url` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `web_app_link_app_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `performance_notify_enabled` tinyint(1) NOT NULL DEFAULT 1,
  `_created_at` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0),
  `_updated_at` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP(0) ON UPDATE CURRENT_TIMESTAMP(0),
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uq_feishu_subject_code`(`code`) USING BTREE,
  INDEX `idx_feishu_subject_enabled`(`enabled`, `sort_order`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of feishu_subject
-- ----------------------------
INSERT INTO `feishu_subject` VALUES ('a0000001-0000-4000-8000-000000000001', 'kzs', '科臻赛', 0, 1, NULL, NULL, 'cli_aa89a8cede789bec', 1, '2026-05-13 13:29:48', '2026-05-13 14:39:52');
INSERT INTO `feishu_subject` VALUES ('a0000002-0000-4000-8000-000000000002', 'lz', '论致', 1, 1, NULL, NULL, 'cli_aa8eb1f731e15bb7', 1, '2026-05-13 13:29:48', '2026-05-13 14:39:59');

SET FOREIGN_KEY_CHECKS = 1;
