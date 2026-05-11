import React from 'react';
import { Route, Routes } from 'react-router-dom';

import Layout from './components/Layout';
import NotFound from './pages/NotFound/NotFound';
import HomePage from './pages/HomePage/HomePage';
import TodoPage from './pages/TodoPage/TodoPage';
import PerformanceListPage from './pages/PerformanceListPage/PerformanceListPage';
import PerformanceDetailPage from './pages/PerformanceDetailPage/PerformanceDetailPage';
import MyPerformancePage from './pages/MyPerformancePage/MyPerformancePage';
import TemplateManagePage from './pages/admin/TemplateManagePage/TemplateManagePage';
import NotificationManagePage from './pages/admin/NotificationManagePage/NotificationManagePage';
import EmployeeManagePage from './pages/admin/EmployeeManagePage/EmployeeManagePage';
import PermissionManagePage from './pages/admin/PermissionManagePage/PermissionManagePage';
import RoleManagePage from './pages/admin/RoleManagePage/RoleManagePage';
import SystemConfigPage from './pages/admin/SystemConfigPage/SystemConfigPage';
import StatisticsMonthsPage from './pages/admin/StatisticsMonthsPage/StatisticsMonthsPage';
import PerformanceCalibrationPage from './pages/admin/PerformanceCalibrationPage/PerformanceCalibrationPage';
import LocalLoginPage from './pages/LocalLoginPage/LocalLoginPage';

const RoutesComponent = () => {
  return (
    <Routes>
      <Route path="login" element={<LocalLoginPage />} />
      <Route element={<Layout />}>
        <Route index element={<HomePage />} />
        <Route path="todo" element={<TodoPage />} />
        <Route path="performances" element={<PerformanceListPage />} />
        <Route path="performance-list" element={<PerformanceListPage />} />
        <Route path="my-performance" element={<MyPerformancePage />} />
        <Route path="performances/:id" element={<PerformanceDetailPage />} />
        <Route path="admin/performance-calibration" element={<PerformanceCalibrationPage />} />
        <Route path="admin/templates" element={<TemplateManagePage />} />
        <Route path="admin/notifications" element={<NotificationManagePage />} />
        <Route path="admin/employees" element={<EmployeeManagePage />} />
        <Route path="admin/roles" element={<RoleManagePage />} />
        <Route path="admin/permissions" element={<PermissionManagePage />} />
        <Route path="admin/statistics-months" element={<StatisticsMonthsPage />} />
        <Route path="admin/system-config" element={<SystemConfigPage />} />
      </Route>
      <Route path="*" element={<NotFound />} />
    </Routes>
  );
};

export default RoutesComponent;
