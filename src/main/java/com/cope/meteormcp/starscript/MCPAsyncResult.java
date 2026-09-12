package com.cope.meteormcp.starscript;

import java.util.Objects;

class MCPAsyncResult {
   private volatile String lastResult = "Loading...";
   private volatile boolean taskInProgress = false;

   public MCPAsyncResult() {
   }

   public String getLastResult() {
      return this.lastResult;
   }

   public void setLastResult(String result) {
      this.lastResult = Objects.requireNonNullElse(result, "");
   }

   public boolean isTaskInProgress() {
      return this.taskInProgress;
   }

   public synchronized boolean tryStartTask() {
      if (this.taskInProgress) {
         return false;
      } else {
         this.taskInProgress = true;
         return true;
      }
   }

   public synchronized void completeTask() {
      this.taskInProgress = false;
   }
}
