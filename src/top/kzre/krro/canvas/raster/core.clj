(ns top.kzre.krro.canvas.raster.core
  "光栅图层实现：通过画布 API 获取底层数组进行批量渲染，提升性能。
   提供激活光栅后端的便捷函数，替换 *merge-layer!* 并注册 :raster 图层。"
  (:require
   [taoensso.tufte :as p]
   [top.kzre.krro.canvas.core.layer.render.composite :as composite]
   [top.kzre.krro.canvas.core.layer.util :as lu]
   [top.kzre.krro.canvas.raster.spec]
   [top.kzre.krro.core.util.promise :as promise])
  (:import
   (java.util UUID)
   (top.kzre.colorutils.color RGB)
   (top.kzre.krro.canvas.core.layer PixelBlitter)
   (top.kzre.krro.util.tile TiledCanvas)))

(defn make-raster-layer
  "创建一个光栅图层，包含一个 TiledCanvas 画布。
  :tile-size   - 瓦片大小（像素）

   可选关键字：
     :id          - 图层唯一标识（关键字，默认自动生成）
     :name        - 图层名称（默认 \"Raster Layer\"）
     :opacity     - 不透明度 0.0~1.0（默认 1.0）
     :blend-mode  - 混合模式关键字（默认 :normal）
     :visible    - 是否可见（默认 true）
     :backend     - 渲染后端（默认 :default）

     其他变换属性 :x, :y, :scale-x, :scale-y, :rotation 等"
  ([tile-size & {:keys [id name opacity blend-mode visible backend canvas
                        channels]
                 :or   {id         (keyword (str "layer-" (UUID/randomUUID)))
                        name       "Raster Layer"
                        opacity    1.0
                        blend-mode :normal
                        visible   true
                        backend    :default
                        ;; 默认 4 通道。即 RGBA
                        channels 4}
                 :as   opts}]
   (let [canvas (or (when canvas
                      (let [actual-tile-size (.getTileSize ^TiledCanvas canvas)]
                        (when-not (= actual-tile-size tile-size)
                          (throw (ex-info "unmatched tile-size" {})))
                        canvas))
                    (TiledCanvas. tile-size
                                  (float-array (repeat channels 0))))]
     (merge {:id         id
             :type       :raster
             :name       name
             :opacity    opacity
             :blend-mode blend-mode
             :visible   visible
             :backend    backend
             :canvas     canvas}
            (select-keys opts [:x :y :scale-x :scale-y :rotation :mask])))))


(defmethod composite/composite-layer :raster
  [layer ^TiledCanvas canvas
   {:keys [view-width view-height
           dirty-tiles subpixel?]
    :or   {subpixel? false}}]
  (let [src-canvas (:canvas layer)
        blend-mode (lu/blend-mode-str (:blend-mode layer) :normal)
        opacity    (float (get layer :opacity 1.0))
        transform  (get layer :transform lu/identity-matrix)
        matrix2d   (float-array transform)]
    (try
      (p/profile {:id :composite-layer}
                 (p/p :blit-canvas
                      (PixelBlitter/blit canvas view-width view-height src-canvas
                                         matrix2d blend-mode opacity
                                         dirty-tiles
                                         subpixel?)))
      ;; 原地写成功：返回同一个 canvas，所有权不动
      (promise/resolved canvas)
      (catch Throwable e
        ;; 失败：canvas 归 render-batch 所有，由它统一清；此处只透传异常
        (promise/rejected e)))))