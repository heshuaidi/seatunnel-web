import { useIntl } from "@umijs/max";
import { Tabs } from "antd";
import React, { useEffect, useState } from "react";

import { batchLinkUpIncrementalApi, seatunnelJobInstanceApi } from "./api";
import BasicInfoSection from "./BasicInfoSection";
import HoconTab from "./tabs/HoconTab";
import IncrementalRunTab from "./tabs/IncrementalRunTab";
import LogTab from "./tabs/LogTab";
import MetricsTab from "./tabs/MetricsTab";
import ScheduleTab from "./tabs/ScheduleTab";
import TableTab from "./tabs/TableTab";
import TaskHeader from "./TaskHeader";

interface TaskDetailPanelProps {
  instanceItem: any;
}

const TaskDetailPanel: React.FC<TaskDetailPanelProps> = ({ instanceItem }) => {
  const intl = useIntl();

  const [logContent, setLogContent] = useState<any>("");
  const [logLoading, setLogLoading] = useState<boolean>(false);
  const [incrementalDetail, setIncrementalDetail] = useState<any>(null);
  const [incrementalLoading, setIncrementalLoading] = useState<boolean>(false);
  const [activeKey, setActiveKey] = useState<string>("log");

  const isIncrementalRun = instanceItem?.runType === "INCREMENTAL";
  const detailItem = incrementalDetail
    ? {
        ...instanceItem,
        ...incrementalDetail,
        jobName: instanceItem?.jobName,
        jobStatus: instanceItem?.jobStatus,
        runtimeConfig:
          incrementalDetail?.generatedHocon || instanceItem?.generatedHocon,
      }
    : instanceItem;

  const fetchLog = async () => {
    try {
      setLogLoading(true);

      const res = await seatunnelJobInstanceApi.getLog(instanceItem?.id);

      setLogContent(
        res?.data ||
          intl.formatMessage({
            id: "pages.job.detail.noLog",
            defaultMessage: "No log available",
          })
      );
    } catch (_err) {
      const errorText = intl.formatMessage({
        id: "pages.job.detail.loadLogFailed",
        defaultMessage: "Failed to load log",
      });

      setLogContent(errorText);
    } finally {
      setLogLoading(false);
    }
  };

  useEffect(() => {
    if (instanceItem?.id && !isIncrementalRun) {
      fetchLog();
    }
  }, [instanceItem?.id, isIncrementalRun]);

  useEffect(() => {
    setActiveKey(isIncrementalRun ? "incremental" : "log");
  }, [instanceItem?.id, isIncrementalRun]);

  useEffect(() => {
    const fetchIncrementalDetail = async () => {
      if (!isIncrementalRun || !instanceItem?.taskId || !instanceItem?.runId) {
        setIncrementalDetail(null);
        return;
      }
      setIncrementalLoading(true);
      setActiveKey("incremental");
      try {
        const res = (await batchLinkUpIncrementalApi.getRun(
          instanceItem.taskId,
          instanceItem.runId,
        )) as any;
        if (res?.code === 0) {
          setIncrementalDetail(res.data);
        } else {
          setIncrementalDetail(instanceItem);
        }
      } finally {
        setIncrementalLoading(false);
      }
    };

    fetchIncrementalDetail();
  }, [isIncrementalRun, instanceItem?.taskId, instanceItem?.runId]);

  if (!instanceItem?.jobStatus) {
    return (
      <div className="flex h-full items-center justify-center text-base text-slate-400">
        {intl.formatMessage({
          id: "pages.job.detail.empty",
          defaultMessage:
            "Please select a run record on the left to view details",
        })}{" "}
        😊
      </div>
    );
  }

  const showTableTab = !isIncrementalRun && ["GUIDE_SINGLE", "GUIDE_MULTI"].includes(
    detailItem?.definitionMode
  );

  const normalTabs = [
    {
      key: "log",
      label: intl.formatMessage({
        id: "pages.job.detail.tabs.log",
        defaultMessage: "Log",
      }),
      children: <LogTab content={logContent} loading={logLoading} />,
    },
    {
      key: "hocon",
      label: intl.formatMessage({
        id: "pages.job.detail.tabs.hocon",
        defaultMessage: "Hocon",
      }),
      children: <HoconTab config={detailItem?.runtimeConfig} />,
    },
    {
      key: "metrics",
      label: intl.formatMessage({
        id: "pages.job.detail.tabs.metrics",
        defaultMessage: "Metrics",
      }),
      children: <MetricsTab instanceItem={detailItem} />,
    },
    {
      key: "schedule",
      label: intl.formatMessage({
        id: "pages.job.detail.tabs.schedule",
        defaultMessage: "Scheduled",
      }),
      children: <ScheduleTab instanceItem={detailItem} />,
    },
    ...(showTableTab
      ? [
          {
            key: "table",
            label: intl.formatMessage({
              id: "pages.job.detail.tabs.table",
              defaultMessage: "Table",
            }),
            children: <TableTab instanceItem={detailItem} />,
          },
        ]
      : []),
  ];

  const incrementalTabs = [
    {
      key: "incremental",
      label: "增量详情",
      children: (
        <IncrementalRunTab item={detailItem} loading={incrementalLoading} />
      ),
    },
    {
      key: "hocon",
      label: intl.formatMessage({
        id: "pages.job.detail.tabs.hocon",
        defaultMessage: "Hocon",
      }),
      children: <HoconTab config={detailItem?.generatedHocon || detailItem?.runtimeConfig} />,
    },
  ];

  const tabs = isIncrementalRun ? incrementalTabs : normalTabs;

  return (
    <div className="h-full bg-slate-50">
      <TaskHeader item={detailItem} />

      <div className="h-[calc(100vh-46px)] overflow-y-auto bg-slate-50">
        <BasicInfoSection item={detailItem} />

        <div className="m-4 rounded-lg bg-white p-4 shadow-[0_1px_3px_rgba(15,23,42,0.04)]">
          <Tabs
            activeKey={activeKey}
            items={tabs}
            onChange={(key) => setActiveKey(key)}
          />
        </div>
      </div>
    </div>
  );
};

export default TaskDetailPanel;
