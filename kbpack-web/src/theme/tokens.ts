import type { ThemeConfig } from 'antd';

/**
 * Ant Design theme tokens mapped from UI design §3.2.
 */
export const themeConfig: ThemeConfig = {
  token: {
    colorPrimary: '#2563EB',
    colorSuccess: '#0F766E',
    colorWarning: '#F59E0B',
    colorError: '#DC2626',
    colorInfo: '#2563EB',
    borderRadius: 8,
    fontFamily:
      '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", sans-serif',
    colorBgLayout: '#F7F8FA',
    colorBgContainer: '#FFFFFF',
    colorText: '#111827',
    colorTextSecondary: '#6B7280',
    colorBorder: '#E5E7EB',
  },
  components: {
    Layout: {
      headerBg: '#FFFFFF',
      siderBg: '#FFFFFF',
      bodyBg: '#F7F8FA',
    },
    Card: { borderRadiusLG: 8 },
    Table: { borderRadius: 8 },
  },
};
