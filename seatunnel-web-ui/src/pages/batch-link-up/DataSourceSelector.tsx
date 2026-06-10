import MysqlIcon from '../data-source/icon/MysqlIcon';
import OracleIcon from '../data-source/icon/OracleIcon';
import { SendOutlined } from '@ant-design/icons';
import { Select } from 'antd';
import PostgreSQL from '../data-source/icon/PsSqlIcon';
import StarRocksIcon from '../data-source/icon/StarRocksIcon';

const { Option } = Select;

type DataSourceType =
  | 'MYSQL'
  | 'ORACLE'
  | 'POSTGRE_SQL'
  | 'STARROCKS'

type DataSourceSelectorProps = {
  type: 'source' | 'target';
  value?: string;
  onChange: (value: string) => void;
  style?: React.CSSProperties;
  dataSources?: DataSourceType[]; // 可配置的数据源列表
};

// 数据源配置映射
const DATA_SOURCE_CONFIG: Record<
  DataSourceType,
  { icon: React.ComponentType<any>; displayName: string }
> = {
  MYSQL: { icon: MysqlIcon, displayName: 'MySQL' },
  ORACLE: { icon: OracleIcon, displayName: 'ORACLE' },
  POSTGRE_SQL: { icon: PostgreSQL, displayName: 'PGSQL' },
  STARROCKS: { icon: StarRocksIcon, displayName: 'StarRocks' }
};

const DEFAULT_TARGET_DATA_SOURCES: DataSourceType[] = [
  'MYSQL',
  'ORACLE',
  'POSTGRE_SQL',
  'STARROCKS'
];

const DEFAULT_SOURCE_DATA_SOURCES: DataSourceType[] =
  DEFAULT_TARGET_DATA_SOURCES;

const DataSourceSelector = ({
  type,
  value,
  onChange,
  style,
  dataSources,
}: DataSourceSelectorProps) => {
  const visibleDataSources = (
    dataSources ||
    (type === 'source' ? DEFAULT_SOURCE_DATA_SOURCES : DEFAULT_TARGET_DATA_SOURCES)
  );

  const renderDataSourceOption = (dataSourceType: DataSourceType) => {
    const config = DATA_SOURCE_CONFIG[dataSourceType];
    if (!config) {
      console.warn(`Unknown data source type: ${dataSourceType}`);
      return null;
    }

    const { icon: IconComponent, displayName } = config;

    return (
      <Option
        style={{ paddingLeft: 12 }}
        value={dataSourceType}
        key={dataSourceType}
        label={displayName}
      >
        <div style={{ display: 'flex', alignItems: 'center' }}>
          <IconComponent />
          <span style={{ marginLeft: 8 }}>{displayName}</span>
        </div>
      </Option>
    );
  };

  return (
    <Select
      showSearch
      value={value}
      placeholder={`Select ${type} data source`}
      optionFilterProp="label"
      onChange={onChange}
      suffixIcon={<SendOutlined />}
      style={style}
      filterOption={(input, option) =>
        String(option?.label ?? '').toLowerCase().includes(input.toLowerCase())
      }
    >
      {visibleDataSources.map(renderDataSourceOption)}
    </Select>
  );
};

export default DataSourceSelector;
