import {
  batchLinkUpIncrementalApi,
  seatunnelJobInstanceApi,
} from "@/pages/batch-link-up/api";
import type { HistoryItem } from "@/pages/batch-link-up/type";
import dayjs from "dayjs";
import { useCallback, useEffect, useMemo, useState } from "react";


export type TimeRangeType = "最近一天" | "最近三天" | "最近一周" | "自定义";

interface UseTaskHistoryParams {
  selectedItem: any;
  statusFilter: string;
}

const incrementalStatusToHistoryStatus = (status?: string) => {
  const value = String(status || "").toUpperCase();
  if (value === "SUCCESS") return "FINISHED";
  if (["FAILED", "CHECK_FAILED"].includes(value)) return "FAILED";
  if (value === "RUNNING") return "RUNNING";
  if (["CREATED", "READY", "SUBMITTED", "VERIFYING"].includes(value)) {
    return "PENDING";
  }
  if (["CANCELED", "CANCELLED"].includes(value)) return "CANCELED";
  return value || "UNKNOWN";
};

const historyStatusToIncrementalStatus = (status?: string) => {
  const value = String(status || "").toUpperCase();
  if (!value || value === "ALL") return undefined;
  if (value === "FINISHED") return "SUCCESS";
  return value;
};

const getHistoryTime = (item: any) =>
  item?.createTime || item?.submitTime || item?.startTime || item?.endTime || "";

const normalizeNormalHistoryItem = (item: any): HistoryItem => ({
  ...item,
  rawId: item?.id,
  runType: "NORMAL",
});

const normalizeIncrementalHistoryItem = (
  item: any,
  selectedItem: any,
): HistoryItem => {
  const status = incrementalStatusToHistoryStatus(item?.status);
  const createTime = item?.createTime || item?.submitTime || item?.startTime || "";
  return {
    ...item,
    id: `incremental-${item?.runId}`,
    rawId: item?.id,
    runType: "INCREMENTAL",
    runId: item?.runId,
    batchId: item?.batchId,
    triggerType: item?.triggerType,
    schedulerRunId: item?.schedulerRunId,
    jobName: selectedItem?.jobName || item?.taskCode || "Incremental Run",
    jobStatus: status,
    time: createTime,
    startTime: item?.startTime || item?.submitTime || createTime,
    endTime: item?.endTime,
    createTime,
    runtimeConfig: item?.generatedHocon,
    generatedHocon: item?.generatedHocon,
    sourceCount: item?.sourceCount,
    sinkCount: item?.sinkCount,
    errorCount: item?.errorCount,
    errorMessage: item?.errorMessage,
  };
};

export const useTaskHistory = ({
  selectedItem,
  statusFilter,
}: UseTaskHistoryParams) => {
  const [historyItems, setHistoryItems] = useState<HistoryItem[]>([]);
  const [loading, setLoading] = useState(false);

  const [keyword, setKeyword] = useState("");
  const [debouncedKeyword, setDebouncedKeyword] = useState("");
  const [timeRangeType, setTimeRangeType] =
    useState<TimeRangeType>("最近一天");

  const [customTimeRange, setCustomTimeRange] = useState<
    [dayjs.Dayjs | null, dayjs.Dayjs | null] | null
  >(null);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setDebouncedKeyword(keyword.trim());
    }, 400);

    return () => window.clearTimeout(timer);
  }, [keyword]);

  const getTimeRangeParams = useCallback(() => {
    const now = dayjs();

    switch (timeRangeType) {
      case "最近一天":
        return {
          queryStartTime: now.subtract(1, "day").format("YYYY-MM-DD HH:mm:ss"),
          queryEndTime: now.format("YYYY-MM-DD HH:mm:ss"),
        };

      case "最近三天":
        return {
          queryStartTime: now.subtract(3, "day").format("YYYY-MM-DD HH:mm:ss"),
          queryEndTime: now.format("YYYY-MM-DD HH:mm:ss"),
        };

      case "最近一周":
        return {
          queryStartTime: now.subtract(7, "day").format("YYYY-MM-DD HH:mm:ss"),
          queryEndTime: now.format("YYYY-MM-DD HH:mm:ss"),
        };

      case "自定义":
        return {
          queryStartTime: customTimeRange?.[0]
            ?.startOf("day")
            .format("YYYY-MM-DD HH:mm:ss"),
          queryEndTime: customTimeRange?.[1]
            ?.endOf("day")
            .format("YYYY-MM-DD HH:mm:ss"),
        };

      default:
        return {
          queryStartTime: undefined,
          queryEndTime: undefined,
        };
    }
  }, [timeRangeType, customTimeRange]);

  const fetchHistory = useCallback(async () => {
    if (!selectedItem?.id) {
      setHistoryItems([]);
      return;
    }

    const { queryStartTime, queryEndTime } = getTimeRangeParams();

    setLoading(true);

    try {
      const [normalRes, incrementalRes] = (await Promise.all([
        seatunnelJobInstanceApi.page({
          pageNum: 1,
          pageSize: 20,
          jobDefinitionId: selectedItem.id,
          keyword: debouncedKeyword || undefined,
          jobStatus:
            statusFilter && statusFilter !== "all" ? statusFilter : undefined,
          queryStartTime,
          queryEndTime,
        }),
        batchLinkUpIncrementalApi.listRuns(selectedItem.id, {
          pageNo: 1,
          pageSize: 100,
          keyword: debouncedKeyword || undefined,
          status: historyStatusToIncrementalStatus(statusFilter),
          startTime: queryStartTime,
          endTime: queryEndTime,
        }),
      ])) as any[];

      const normalItems =
        normalRes?.code === 0
          ? (normalRes?.data?.bizData || []).map(normalizeNormalHistoryItem)
          : [];
      const incrementalItems =
        incrementalRes?.code === 0
          ? (incrementalRes?.data?.bizData || []).map((item: any) =>
              normalizeIncrementalHistoryItem(item, selectedItem),
            )
          : [];

      setHistoryItems(
        [...normalItems, ...incrementalItems].sort((left, right) => {
          const leftTime = dayjs(getHistoryTime(left)).valueOf() || 0;
          const rightTime = dayjs(getHistoryTime(right)).valueOf() || 0;
          return rightTime - leftTime;
        }),
      );
    } catch (_error) {
      setHistoryItems([]);
    } finally {
      setLoading(false);
    }
  }, [
    selectedItem?.id,
    debouncedKeyword,
    statusFilter,
    getTimeRangeParams,
  ]);

  useEffect(() => {
    fetchHistory();
  }, [fetchHistory]);

  const statusCountMap = useMemo(() => {
    return historyItems.reduce(
      (acc, item) => {
        const status = item.jobStatus || "UNKNOWN";
        acc[status] = (acc[status] || 0) + 1;
        acc.all += 1;
        return acc;
      },
      {
        all: 0,
        FINISHED: 0,
        FAILED: 0,
        RUNNING: 0,
        PENDING: 0,
      } as Record<string, number>
    );
  }, [historyItems]);

  return {
    historyItems,
    loading,

    keyword,
    setKeyword,

    timeRangeType,
    setTimeRangeType,

    customTimeRange,
    setCustomTimeRange,

    statusCountMap,
    fetchHistory,
  };
};
