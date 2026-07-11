import {
  CheckCircleOutlined,
  CloudUploadOutlined,
  FileZipOutlined,
  InboxOutlined,
} from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  App,
  Button,
  Descriptions,
  Form,
  Input,
  Progress,
  Result,
  Select,
  Space,
  Steps,
  Typography,
  Upload,
} from 'antd';
import { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { listCollections, type CollectionItem } from '../api/collections';
import { getApiErrorMessage } from '../api/client';
import { uploadPackage, type UploadMetadata, type UploadResult } from '../api/packages';
import { listTags } from '../api/tags';
import { getVersion } from '../api/versions';
import { StatusTag } from '../components/package/StatusTag';
import { formatBytes } from '../utils/format';

const { Dragger } = Upload;

function flattenCollections(items: CollectionItem[], level = 0): Array<CollectionItem & { level: number }> {
  return items.flatMap((item) => [
    { ...item, level },
    ...flattenCollections(item.children || [], level + 1),
  ]);
}

export function UploadPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [params] = useSearchParams();
  const { message } = App.useApp();
  const [form] = Form.useForm<UploadMetadata>();
  const [current, setCurrent] = useState(0);
  const [file, setFile] = useState<File>();
  const [progress, setProgress] = useState(0);
  const [result, setResult] = useState<UploadResult>();
  const targetPackageId = params.get('packageId') || undefined;
  const tags = useQuery({ queryKey: ['tags'], queryFn: listTags });
  const collections = useQuery({ queryKey: ['collections'], queryFn: listCollections });

  const uploadMutation = useMutation({
    mutationFn: (values: UploadMetadata) =>
      uploadPackage(file!, { ...values, target_package_id: targetPackageId }, setProgress),
    onSuccess: (data) => {
      setResult(data);
      setCurrent(2);
      void queryClient.invalidateQueries({ queryKey: ['packages'] });
    },
    onError: (error) => message.error(getApiErrorMessage(error, '上传失败，请检查文件和限制配置')),
  });

  const version = useQuery({
    queryKey: ['version', result?.package_id, result?.version_id],
    queryFn: () => getVersion(result!.package_id, result!.version_id),
    enabled: Boolean(result?.package_id && result?.version_id),
    refetchInterval: (query) => {
      const status = query.state.data?.parse_status?.toLowerCase();
      return status === 'pending' || status === 'processing' ? 2000 : false;
    },
    retry: 3,
    retryDelay: 1200,
  });

  const parseStatus = (version.data?.parse_status || result?.parse_status || 'pending').toLowerCase();
  const isParsing = parseStatus === 'pending' || parseStatus === 'processing';
  const isSuccess = parseStatus === 'success';
  const isFailed = parseStatus === 'failed';
  const collectionOptions = flattenCollections(collections.data || []);

  const chooseFile = (
    <div className="upload-step-panel">
      <Dragger
        accept=".zip,.tar.gz,.tgz,.html,.htm,application/zip,text/html"
        maxCount={1}
        fileList={file ? [{ uid: file.name, name: file.name, status: 'done', size: file.size }] : []}
        beforeUpload={(nextFile) => {
          setFile(nextFile);
          return false;
        }}
        onRemove={() => {
          setFile(undefined);
          return true;
        }}
      >
        <p className="ant-upload-drag-icon"><InboxOutlined /></p>
        <p className="ant-upload-text">上传 HTML 知识包</p>
        <p className="ant-upload-hint">支持 zip、tar.gz 或单个 html，建议将入口文件与资源一起打包。</p>
      </Dragger>
      <div className="upload-step-actions">
        <Button
          type="primary"
          disabled={!file}
          onClick={() => {
            if (!file) return;
            if (!form.getFieldValue('title')) form.setFieldValue('title', file.name.replace(/\.(tar\.gz|tgz|zip|html?)$/i, ''));
            setCurrent(1);
          }}
        >
          下一步
        </Button>
      </div>
    </div>
  );

  const metadata = (
    <div className="upload-step-panel">
      {targetPackageId ? (
        <Alert
          type="info"
          showIcon
          message="上传新版本"
          description={`文件将作为知识包 ${targetPackageId} 的新版本。`}
        />
      ) : null}
      <Form
        form={form}
        layout="vertical"
        initialValues={{ source_type: 'manual' }}
        onFinish={(values) => uploadMutation.mutate(values)}
      >
        <div className="form-grid">
          <Form.Item name="title" label="标题" rules={[{ required: true, message: '请输入知识包标题' }]}>
            <Input maxLength={200} />
          </Form.Item>
          <Form.Item name="source_name" label="来源名称">
            <Input placeholder="例如：项目文档、培训材料" maxLength={64} />
          </Form.Item>
        </div>
        <Form.Item name="description" label="描述">
          <Input.TextArea rows={4} maxLength={2000} showCount />
        </Form.Item>
        <div className="form-grid">
          <Form.Item name="collection_ids" label="集合">
            <Select
              mode="multiple"
              allowClear
              placeholder="选择集合"
              loading={collections.isPending}
              options={collectionOptions.map((item) => ({
                value: item.id,
                label: `${'　'.repeat(item.level)}${item.name}`,
              }))}
            />
          </Form.Item>
          <Form.Item name="tag_names" label="标签">
            <Select
              mode="tags"
              allowClear
              placeholder="选择或输入标签"
              loading={tags.isPending}
              options={(tags.data || []).map((tag) => ({ value: tag.name, label: tag.name }))}
            />
          </Form.Item>
          <Form.Item name="source_type" label="来源类型">
            <Select options={[{ value: 'manual', label: '手工上传' }, { value: 'ai_generated', label: 'AI 生成' }, { value: 'imported', label: '外部导入' }]} />
          </Form.Item>
          <Form.Item name="entry_file" label="入口文件（可选）">
            <Input placeholder="默认自动识别，如 index.html" />
          </Form.Item>
        </div>
        <div className="selected-file-summary">
          <FileZipOutlined />
          <span>{file?.name}</span>
          <Typography.Text type="secondary">{formatBytes(file?.size)}</Typography.Text>
        </div>
        {uploadMutation.isPending ? (
          <Progress percent={progress} status="active" strokeColor="#2563EB" />
        ) : null}
        <div className="upload-step-actions">
          <Button disabled={uploadMutation.isPending} onClick={() => setCurrent(0)}>上一步</Button>
          <Button type="primary" htmlType="submit" loading={uploadMutation.isPending} icon={<CloudUploadOutlined />}>
            开始上传
          </Button>
        </div>
      </Form>
    </div>
  );

  const parsePreview = (
    <div className="upload-step-panel parse-panel">
      <div className={`parse-indicator ${isFailed ? 'failed' : isSuccess ? 'success' : ''}`}>
        {isSuccess ? <CheckCircleOutlined /> : <CloudUploadOutlined />}
      </div>
      <Typography.Title level={3}>
        {isSuccess ? '解析完成' : isFailed ? '解析失败' : '正在解析知识包'}
      </Typography.Title>
      <Typography.Paragraph type="secondary">
        {isSuccess
          ? '入口文件、章节和索引已经准备完成。'
          : isFailed
            ? version.data?.parse_error || '原始文件已保存，可在详情页指定入口后重新解析。'
            : '任务在后台继续执行，离开页面不会中断解析。'}
      </Typography.Paragraph>
      <div className="parse-stages">
        <Progress
          percent={isSuccess || isFailed ? 100 : parseStatus === 'processing' ? 68 : 28}
          status={isFailed ? 'exception' : isSuccess ? 'success' : 'active'}
          showInfo={false}
        />
        <Space wrap>
          <StatusTag status={parseStatus} />
          <Typography.Text type="secondary">解压 · 识别入口 · 抽取文本 · 建立索引</Typography.Text>
        </Space>
      </div>
      {version.data ? (
        <Descriptions className="parse-facts" bordered size="small" column={{ xs: 1, sm: 2 }}>
          <Descriptions.Item label="版本">v{version.data.version_no}</Descriptions.Item>
          <Descriptions.Item label="入口文件">{version.data.entry_file || '自动识别中'}</Descriptions.Item>
          <Descriptions.Item label="文件数">{version.data.file_count ?? '—'}</Descriptions.Item>
          <Descriptions.Item label="解压大小">{formatBytes(version.data.unpacked_size)}</Descriptions.Item>
        </Descriptions>
      ) : null}
      {version.isError ? (
        <Alert type="warning" showIcon message="暂时无法读取解析进度" description={getApiErrorMessage(version.error)} />
      ) : null}
      <div className="upload-step-actions">
        <Button onClick={() => result && navigate(`/packages/${result.package_id}`)}>在详情页查看</Button>
        <Button type="primary" disabled={isParsing && !version.isError} onClick={() => setCurrent(3)}>
          {isFailed ? '完成' : '下一步'}
        </Button>
      </div>
    </div>
  );

  const done = (
    <Result
      status={isFailed ? 'warning' : 'success'}
      title={isFailed ? '文件已保存，解析需要处理' : '知识包已入库'}
      subTitle={isFailed ? '可以进入详情页手工指定入口文件并重新解析。' : '现在可以预览原始内容或进入详情查看章节。'}
      extra={[
        <Button key="detail" type="primary" onClick={() => result && navigate(`/packages/${result.package_id}`)}>查看详情</Button>,
        isSuccess && result ? <Button key="preview" onClick={() => navigate(`/packages/${result.package_id}/preview/${result.version_id}`)}>原样预览</Button> : null,
        <Button key="again" onClick={() => {
          setCurrent(0);
          setFile(undefined);
          setResult(undefined);
          setProgress(0);
          form.resetFields();
        }}>继续上传</Button>,
      ].filter(Boolean)}
    />
  );

  return (
    <div className="upload-page">
      <div className="page-heading">
        <div>
          <span className="eyebrow">Ingest knowledge</span>
          <Typography.Title level={1}>{targetPackageId ? '上传新版本' : '上传知识包'}</Typography.Title>
          <Typography.Paragraph type="secondary">文件上传后由后台完成安全校验、内容抽取和索引建立。</Typography.Paragraph>
        </div>
      </div>
      <Steps
        className="upload-steps"
        current={current}
        responsive={false}
        items={[{ title: '选择文件' }, { title: '填写信息' }, { title: '解析预检' }, { title: '完成' }]}
      />
      <section className="surface upload-workspace">
        {current === 0 ? chooseFile : null}
        {current === 1 ? metadata : null}
        {current === 2 ? parsePreview : null}
        {current === 3 ? done : null}
      </section>
    </div>
  );
}
