import { Button, Modal, Splitter } from "antd";
import React, { forwardRef, useImperativeHandle, useRef, useState } from "react";
import { useIntl } from "@umijs/max";
import { DatabaseZap } from "lucide-react";

import "./sumary.less";
import IncrementalControlDrawer from "./config/script/IncrementalControlDrawer";
import TaskDetailPanel from "./TaskDetailPanel";
import TaskHistoryPanel from "./components/TaskHistoryPanel";


interface CreateModalProps {
  onCreate?: (values: any) => void;
}

const TaskViewModal = forwardRef((_props: CreateModalProps, ref) => {
  const intl = useIntl();

  const [visible, setVisible] = useState<boolean>(false);
  const [jobItem, setJobItem] = useState<any>(null);
  const [instanceItem, setInstanceItem] = useState<any>(null);
  const [statusFilter, setStatusFilter] = useState<string>("all");
  const [incrementalOpen, setIncrementalOpen] = useState<boolean>(false);

  const callback = useRef<() => void>(() => {
    return;
  });

  const onClose = () => {
    setVisible(false);
    setInstanceItem(null);
    setJobItem(null);
    setStatusFilter("all");
    setIncrementalOpen(false);
  };

  const onOpen = (status: boolean, record: any, cbk: () => void) => {
    setVisible(status);
    setJobItem(record);
    setInstanceItem(null);
    callback.current = cbk;
  };

  useImperativeHandle(ref, () => ({
    onOpen,
  }));

  return (
    <Modal
      title={null}
      open={visible}
      onCancel={onClose}
      maskStyle={{ background: "#f2f4f7f2" }}
      destroyOnClose
      className="custom-modal"
      maskClosable={false}
      style={{ top: 10 }}
      width="99vw"
      footer={null}
    >
      <div style={{ height: "calc(100vh - 43px)", padding: 16 }}>
        <div className="mb-4 flex items-center justify-between">
          <div style={{ fontSize: 18, fontWeight: 600 }}>
            {intl.formatMessage({
              id: "pages.job.history.title",
              defaultMessage: "Run History",
            })}
          </div>
          <Button
            icon={<DatabaseZap size={15} />}
            onClick={() => setIncrementalOpen(true)}
          >
            增量控制
          </Button>
        </div>

        <Splitter
          style={{
            height: "calc(100% - 43px)",
            boxShadow: "0 0 10px rgba(0, 0, 0, 0.1)",
          }}
        >
          <Splitter.Panel defaultSize="22%" min="22%" max="70%">
            <TaskHistoryPanel
              selectedItem={jobItem}
              statusFilter={statusFilter}
              onItemSelect={() => {}}
              onStatusFilterChange={setStatusFilter}
              setInstanceItem={setInstanceItem}
              instanceItem={instanceItem}
            />
          </Splitter.Panel>

          <Splitter.Panel>
            <TaskDetailPanel instanceItem={instanceItem} />
          </Splitter.Panel>
        </Splitter>
      </div>

      <IncrementalControlDrawer
        taskId={jobItem?.id}
        open={incrementalOpen}
        onClose={() => setIncrementalOpen(false)}
        onInsertPlaceholder={() => {}}
        scene="detail"
        releaseState={jobItem?.releaseState}
        sourceDatasourceId={jobItem?.sourceDatasourceId}
        sinkDatasourceId={jobItem?.sinkDatasourceId}
        sourceDbType={jobItem?.sourceType}
        sinkDbType={jobItem?.sinkType}
        readOnly
      />
    </Modal>
  );
});

export default TaskViewModal;
