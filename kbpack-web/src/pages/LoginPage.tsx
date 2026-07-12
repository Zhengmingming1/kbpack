import { App, Button, Form, Input, Typography } from 'antd';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { login } from '../api/auth';
import { getApiErrorMessage } from '../api/client';
import { BRAND_MARK_PATH, BRAND_NAME } from '../brand';

export function LoginPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const queryClient = useQueryClient();
  const { message } = App.useApp();
  const [loading, setLoading] = useState(false);

  return (
    <main className="login-page">
      <section className="login-panel">
        <div className="login-brand">
          <img className="brand-mark" src={BRAND_MARK_PATH} alt="" aria-hidden="true" />
          <div>
            <Typography.Title level={2}>{BRAND_NAME}</Typography.Title>
            <Typography.Text type="secondary">登录以继续管理你的知识资产</Typography.Text>
          </div>
        </div>
        <Form
          layout="vertical"
          onFinish={async (values) => {
            setLoading(true);
            try {
              const user = await login(values.username, values.password);
              queryClient.setQueryData(['auth', 'me'], user);
              message.success('登录成功');
              const from = searchParams.get('from');
              navigate(from?.startsWith('/') ? from : '/', { replace: true });
            } catch (error: unknown) {
              message.error(getApiErrorMessage(error, '登录失败，请检查用户名和密码'));
            } finally {
              setLoading(false);
            }
          }}
        >
          <Form.Item label="用户名" name="username" rules={[{ required: true }]}>
            <Input autoComplete="username" />
          </Form.Item>
          <Form.Item label="密码" name="password" rules={[{ required: true }]}>
            <Input.Password autoComplete="current-password" />
          </Form.Item>
          <Button type="primary" htmlType="submit" block loading={loading}>
            登录
          </Button>
        </Form>
        <Typography.Paragraph className="login-footnote" type="secondary">
          使用首次启动时配置的管理员账号登录。
        </Typography.Paragraph>
      </section>
    </main>
  );
}
