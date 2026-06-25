#!/usr/local/bin/python3
import cv2
import numpy as np
from ultralytics import YOLO
import torch
import threading
import time


class MultiPlugDetector:
    def __init__(self, model_path):
        """
        Initialize multi-plug detector
        model_path: Path to trained model (.pt file)
        """
        # Check if GPU is available
        self.device = 'cuda' if torch.cuda.is_available() else 'cpu'
        print(f"Using device: {self.device}")

        # Load trained model and move to GPU
        print(f"Loading model: {model_path}")
        self.model = YOLO(model_path)
        self.model.to(self.device)
        print("Model loaded successfully")
        
        # Get actual class names from model
        self.model_classes = self.model.names
        print(f"Model actual classes: {self.model_classes}")
        
        # Use model's actual class names
        self.class_names = self.model_classes

        # Y-difference thresholds for each bottom (8 thresholds)
        # Format: {column_index: {'front': front_threshold, 'back': back_threshold}}
        # Column index starts from 0 (left to right)
        self.Y_DIFF_THRESHOLDS = {
            0: {'front': 210, 'back': 180},   # Column 1 (leftmost)(从左往右数，210是第一个)
            1: {'front': 275, 'back': 215},   # Column 2
            2: {'front': 275, 'back': 222},   # Column 3
            3: {'front': 210, 'back': 180}    # Column 4 (rightmost)
        }
        
        # X-direction matching tolerance (to determine which column a top belongs to)
        self.COLUMN_X_TOLERANCE = 210  # Increased for better matching
        
        # Detection confidence threshold
        self.confidence_threshold = 0.25

        # Multi-threading related
        self.latest_frame = None
        self.latest_result = None
        self.frame_lock = threading.Lock()
        self.processing = False
        self.processing_thread = None

        # FPS calculation
        self.frame_count = 0
        self.fps = 0
        self.last_time = time.time()
        
        # Debug information
        self.detection_log = []
        
        # Store status of 4 tops
        self.top_status = {
            0: {'y_diff': 0, 'x_diff_front': 0, 'x_diff_back': 0, 'y_diff_front': 0, 'y_diff_back': 0,
                'status': 'Not detected', 'bottom_type': None, 'matched_bottom_idx': -1, 'threshold': 0,
                'detected': False, 'matched': False, 'column': -1},
            1: {'y_diff': 0, 'x_diff_front': 0, 'x_diff_back': 0, 'y_diff_front': 0, 'y_diff_back': 0,
                'status': 'Not detected', 'bottom_type': None, 'matched_bottom_idx': -1, 'threshold': 0,
                'detected': False, 'matched': False, 'column': -1},
            2: {'y_diff': 0, 'x_diff_front': 0, 'x_diff_back': 0, 'y_diff_front': 0, 'y_diff_back': 0,
                'status': 'Not detected', 'bottom_type': None, 'matched_bottom_idx': -1, 'threshold': 0,
                'detected': False, 'matched': False, 'column': -1},
            3: {'y_diff': 0, 'x_diff_front': 0, 'x_diff_back': 0, 'y_diff_front': 0, 'y_diff_back': 0,
                'status': 'Not detected', 'bottom_type': None, 'matched_bottom_idx': -1, 'threshold': 0,
                'detected': False, 'matched': False, 'column': -1}
        }

    """Process frame asynchronously"""
    def process_frame_async(self, frame):
        with self.frame_lock:
            self.latest_frame = frame.copy()

        if (
            not self.processing
            or self.processing_thread is None
            or not self.processing_thread.is_alive()
        ):
            self.processing = True
            self.processing_thread = threading.Thread(
                target=self._process_thread, daemon=True
            )
            self.processing_thread.start()

    """ Inference function """
    def _process_thread(self):
        while self.processing:
            # Get current frame (if any)
            with self.frame_lock:
                if self.latest_frame is not None:
                    frame = self.latest_frame.copy()
                    self.latest_frame = None  # Clear for new frame
                else:
                    frame = None

            if frame is not None:
                try:
                    # Record start time
                    start_detect = time.time()
                    
                    # Perform detection
                    results = self.model(frame, verbose=False, imgsz=640, 
                                        conf=self.confidence_threshold, iou=0.45)
                    
                    detect_time = time.time() - start_detect
                    print(f"Detection time: {detect_time:.3f}s, Frame size: {frame.shape}")

                    # Parse detection results
                    boxes = []
                    class_ids = []
                    confidences = []

                    for result in results:
                        num_boxes = len(result.boxes)
                        print(f"Detected {num_boxes} objects")
                        
                        for box in result.boxes:
                            xyxy = box.xyxy[0].cpu().numpy()
                            class_id = int(box.cls[0])
                            conf = float(box.conf[0])

                            if conf >= self.confidence_threshold:
                                x1, y1, x2, y2 = xyxy
                                class_name = self.class_names.get(class_id, f'class_{class_id}')
                                print(f"  {class_name}: conf={conf:.2f}, box=[{x1:.0f},{y1:.0f},{x2:.0f},{y2:.0f}]")
                                
                                x_center = (x1 + x2) / 2
                                y_center = (y1 + y2) / 2
                                width = x2 - x1
                                height = y2 - y1

                                boxes.append([x_center, y_center, width, height])
                                class_ids.append(class_id)
                                confidences.append(conf)

                    # Process results
                    processed_frame, status_text, alert = self._process_detections(
                        frame, boxes, class_ids, confidences
                    )

                    with self.frame_lock:
                        self.latest_result = (processed_frame, status_text, alert)
                        
                    # Log detection results
                    self.detection_log.append({
                        'time': time.time(),
                        'num_boxes': len(boxes),
                        'classes': class_ids
                    })

                except Exception as e:
                    print(f"Processing error: {e}")
                    with self.frame_lock:
                        self.latest_result = (frame, f"Error: {str(e)[:50]}", True)

            # Brief sleep
            time.sleep(0.01)

        print("Processing thread exited")

    """Process detection results - main logic - SIMPLIFIED MATCHING"""
    def _process_detections(self, frame, boxes, class_ids, confidences):
        result_frame = frame.copy()
        top_marks = []    # top labels
        bottom_marks = [] # bottom labels

        # === 新增：输出检测数量 ===
        top_count = class_ids.count(0) if class_ids else 0
        bottom_count = class_ids.count(1) if class_ids else 0
        total_count = len(class_ids)
    
        print(f"[FRAME] Detected: {total_count} objects | Top: {top_count}/4, Bottom: {bottom_count}/8")
        if total_count != 12:
            print(f"  WARNING: Expected 12 objects (4 tops + 8 bottoms), got {total_count}")
    # === 新增结束 ===

        # Get actual frame dimensions
        frame_height, frame_width = frame.shape[:2]
        
        # Draw all detected objects
        for box, class_id, conf in zip(boxes, class_ids, confidences):
            x_center, y_center, width, height = box
            x_center_pixel = int(x_center)
            y_center_pixel = int(y_center)
            width_pixel = int(width)
            height_pixel = int(height)

            # Calculate bounding box coordinates
            x1 = int(x_center_pixel - width_pixel / 2)
            y1 = int(y_center_pixel - height_pixel / 2)
            x2 = int(x_center_pixel + width_pixel / 2)
            y2 = int(y_center_pixel + height_pixel / 2)

            # Ensure coordinates are within image bounds
            x1 = max(0, min(x1, frame_width - 1))
            y1 = max(0, min(y1, frame_height - 1))
            x2 = max(0, min(x2, frame_width - 1))
            y2 = max(0, min(y2, frame_height - 1))
            
            # Recalculate center point
            x_center_pixel = (x1 + x2) // 2
            y_center_pixel = (y1 + y2) // 2

            # Draw bounding box and label
            color = (0, 255, 0) if class_id == 0 else (255, 0, 0)
            label = self.class_names.get(class_id, f'class_{class_id}')

            cv2.rectangle(result_frame, (x1, y1), (x2, y2), color, 2)
            cv2.putText(
                result_frame,
                f'{label} {conf:.2f}',
                (x1, y1 - 10),
                cv2.FONT_HERSHEY_SIMPLEX,
                0.8,
                color,
                2,
            )

            # Draw center point
            cv2.circle(result_frame, (x_center_pixel, y_center_pixel), 5, color, -1)

            # Save marker position
            mark_data = {
                'box': box,
                'pixel_pos': (x_center_pixel, y_center_pixel),
                'conf': conf,
                'class_id': class_id
            }

            if class_id == 0:  # top
                top_marks.append(mark_data)
            elif class_id == 1:  # bottom
                bottom_marks.append(mark_data)

        # Core logic: 4 tops match 8 bottoms - SIMPLIFIED APPROACH
        alert = False
        overall_status = "Detecting..."
        
        # Reset all top status first
        for i in range(4):
            self.top_status[i] = {'y_diff': 0, 'x_diff_front': 0, 'x_diff_back': 0, 'y_diff_front': 0, 'y_diff_back': 0,
                                 'status': 'Not detected', 'bottom_type': None, 'matched_bottom_idx': -1, 'threshold': 0,
                                 'detected': False, 'matched': False, 'column': -1}
        
        # If we have tops and bottoms
        if len(top_marks) > 0 and len(bottom_marks) >= 8:
            print(f"✓ Detected {len(top_marks)} tops, {len(bottom_marks)} bottoms")
            
            # Sort tops by x-coordinate (left to right)
            top_marks.sort(key=lambda p: p['pixel_pos'][0])
            
            # Sort bottoms by x-coordinate (left to right)
            bottom_marks.sort(key=lambda p: p['pixel_pos'][0])
            
            # 根据X坐标顺序分配底部类型：前1、后1、前2、后2、后3、前3、后4、前4
            # 给每个底部标记类型和列索引
            bottom_types = []
            bottom_columns = []
            
            # 根据X坐标顺序分配类型
            expected_order = ['front', 'back', 'front', 'back', 'back', 'front', 'back', 'front']
            expected_columns = [0, 0, 1, 1, 2, 2, 3, 3]  # 对应的列索引
            
            for idx, bottom_mark in enumerate(bottom_marks):
                if idx < 8:  # 只处理前8个底部
                    bottom_type = expected_order[idx]
                    column_idx = expected_columns[idx]
                    bottom_types.append(bottom_type)
                    bottom_columns.append(column_idx)
                    
                    # 在底部旁边显示类型信息（调试用）
                    x, y = bottom_mark['pixel_pos']
                    cv2.putText(
                        result_frame,
                        f"{bottom_type[0].upper()}{column_idx+1}",
                        (x + 10, y),
                        cv2.FONT_HERSHEY_SIMPLEX,
                        0.6,
                        (255, 255, 0),  # 青色
                        2,
                    )
                else:
                    bottom_types.append('unknown')
                    bottom_columns.append(-1)
            
            # SIMPLE FIXED ASSIGNMENT: Assume tops are in correct order and spaced evenly
            # For each top, find the two closest bottoms in x-direction
            
            for top_idx, top_mark in enumerate(top_marks):
                if top_idx >= 4:  # Process at most 4 tops
                    break
                    
                top_x, top_y = top_mark['pixel_pos']
                
                # Mark this top as detected
                self.top_status[top_idx]['detected'] = True
                
                # Find ALL bottoms sorted by x-distance to this top
                bottom_distances = []
                for bottom_idx, bottom_mark in enumerate(bottom_marks):
                    if bottom_idx >= len(bottom_types):  # 跳过没有类型的底部
                        continue
                        
                    bottom_x, bottom_y = bottom_mark['pixel_pos']
                    x_distance = abs(top_x - bottom_x)
                    y_distance = abs(top_y - bottom_y)
                    
                    bottom_distances.append({
                        'bottom_idx': bottom_idx,
                        'bottom_mark': bottom_mark,
                        'x_distance': x_distance,
                        'y_distance': y_distance,
                        'bottom_x': bottom_x,
                        'bottom_y': bottom_y,
                        'bottom_type': bottom_types[bottom_idx],
                        'column_idx': bottom_columns[bottom_idx]
                    })
                
                # Sort by x-distance
                bottom_distances.sort(key=lambda x: x['x_distance'])
                
                # Take the two closest bottoms
                if len(bottom_distances) >= 2:
                    closest1 = bottom_distances[0]
                    closest2 = bottom_distances[1]
                    
                    # 根据X距离判断哪个更近，使用预分配的底部类型
                    if closest1['x_distance'] < closest2['x_distance']:
                        # 第一个底部更近
                        target_bottom = closest1
                        alternative_bottom = closest2
                    else:
                        # 第二个底部更近
                        target_bottom = closest2
                        alternative_bottom = closest1
                    
                    # 获取目标底部的信息
                    bottom_type = target_bottom['bottom_type']
                    matched_column_idx = target_bottom['column_idx']
                    
                    # 计算距离
                    x_diff = target_bottom['x_distance']
                    y_diff = target_bottom['y_distance']
                    
                    # 计算替代底部的距离（用于显示）
                    alt_x_diff = alternative_bottom['x_distance']
                    alt_y_diff = alternative_bottom['y_distance']
                    alt_bottom_type = alternative_bottom['bottom_type']
                    
                    # 根据目标底部类型设置front/back距离
                    if bottom_type == 'front':
                        front_x_dist = x_diff
                        front_y_dist = y_diff
                        back_x_dist = alt_x_diff if alt_bottom_type == 'back' else 9999
                        back_y_dist = alt_y_diff if alt_bottom_type == 'back' else 9999
                    else:  # bottom_type == 'back'
                        back_x_dist = x_diff
                        back_y_dist = y_diff
                        front_x_dist = alt_x_diff if alt_bottom_type == 'front' else 9999
                        front_y_dist = alt_y_diff if alt_bottom_type == 'front' else 9999
                    
                    # Use threshold from configuration
                    if matched_column_idx in self.Y_DIFF_THRESHOLDS:
                        threshold = self.Y_DIFF_THRESHOLDS[matched_column_idx][bottom_type]
                    else:
                        # 如果列索引无效，使用默认值
                        threshold = self.Y_DIFF_THRESHOLDS[0]['front']
                    
                    # Check insertion status
                    if y_diff <= threshold:
                        status = "OK"
                        top_color = (0, 255, 0)  # Green
                        alert = False
                    else:
                        status = "NG"
                        top_color = (0, 0, 255)  # Red
                        alert = True
                    
                    # Update status
                    self.top_status[top_idx] = {
                        'y_diff': y_diff,
                        'x_diff_front': front_x_dist,
                        'x_diff_back': back_x_dist,
                        'y_diff_front': front_y_dist,
                        'y_diff_back': back_y_dist,
                        'status': status,
                        'bottom_type': bottom_type,
                        'matched_bottom_idx': target_bottom['bottom_idx'],
                        'threshold': threshold,
                        'detected': True,
                        'matched': True,
                        'column': matched_column_idx
                    }
                    
                    # Draw connection line to the selected bottom
                    target_pos = target_bottom['bottom_mark']['pixel_pos']
                    cv2.line(result_frame, 
                            (top_x, top_y), 
                            target_pos, 
                            top_color, 2)
                    
                    # Display distances near the top
                    # Line 1: Top number
                    cv2.putText(
                        result_frame,
                        f"T{top_idx+1}",
                        (top_x - 20, top_y + 30),
                        cv2.FONT_HERSHEY_SIMPLEX,
                        0.6,
                        top_color,
                        2,
                    )
                    
                    # Line 2: Selected bottom info
                    cv2.putText(
                        result_frame,
                        f"Y:{y_diff:.0f}",
                        (top_x - 30, top_y - 20),
                        cv2.FONT_HERSHEY_SIMPLEX,
                        0.9,
                        (0, 0, 255),
                        2,
                    )
                    
                    # Debug output
                    print(f"Top{top_idx+1}:")
                    print(f"  At position: ({top_x}, {top_y})")
                    print(f"  Target: {bottom_type}{matched_column_idx+1}, X={x_diff:.0f}, Y={y_diff:.0f}")
                    print(f"  Alternative: {alt_bottom_type}, X={alt_x_diff:.0f}, Y={alt_y_diff:.0f}")
                    print(f"  Selected: {bottom_type}, Y-diff={y_diff:.0f}, Threshold={threshold}")
                    
                else:
                    # Not enough bottoms
                    self.top_status[top_idx] = {
                        'y_diff': 0, 'x_diff_front': 0, 'x_diff_back': 0, 'y_diff_front': 0, 'y_diff_back': 0,
                        'status': 'Insufficient bottoms',
                        'bottom_type': None,
                        'matched_bottom_idx': -1,
                        'threshold': 0,
                        'detected': True,
                        'matched': False,
                        'column': -1
                    }
                
                # Always draw the top number even if not fully matched
                cv2.putText(
                    result_frame,
                    f"T{top_idx+1}",
                    (top_x - 20, top_y + 30),
                    cv2.FONT_HERSHEY_SIMPLEX,
                    0.6,
                    (255, 255, 0) if not self.top_status[top_idx]['matched'] else (0, 255, 0),
                    2,
                )
        
        elif len(top_marks) > 0:
            # Some tops detected but not enough bottoms
            for top_idx, top_mark in enumerate(top_marks):
                if top_idx < 4:
                    top_x, top_y = top_mark['pixel_pos']
                    self.top_status[top_idx] = {
                        'y_diff': 0, 'x_diff_front': 0, 'x_diff_back': 0, 'y_diff_front': 0, 'y_diff_back': 0,
                        'status': f'Bottoms: {len(bottom_marks)}/8',
                        'bottom_type': None,
                        'matched_bottom_idx': -1,
                        'threshold': 0,
                        'detected': True,
                        'matched': False,
                        'column': -1
                    }
                    
                    # Draw the top number
                    cv2.putText(
                        result_frame,
                        f"T{top_idx+1}",
                        (top_x - 20, top_y + 30),
                        cv2.FONT_HERSHEY_SIMPLEX,
                        0.6,
                        (255, 255, 0),  # Yellow for unmatched
                        2,
                    )
        
        # Display status of 4 tops with detailed distance information
        self._display_top_status(result_frame)
        
        # Update overall status
        matched_tops = [status for status in self.top_status.values() if status['detected'] and status['matched']]
        
        if len(top_marks) >= 4 and len(bottom_marks) >= 8 and len(matched_tops) == 4:
            all_ok = all(status['status'] == 'OK' for status in matched_tops)
            overall_status = "All OK" if all_ok else "Some NG"
        elif len(top_marks) < 4:
            overall_status = f"Top:{len(top_marks)}/4"
            alert = True
        elif len(bottom_marks) < 8:
            overall_status = f"Bottom:{len(bottom_marks)}/8"
            alert = True
        else:
            overall_status = f"Matched:{len(matched_tops)}/4"
            if len(matched_tops) < 4:
                alert = True

        # Display FPS
        current_time = time.time()
        self.frame_count += 1
        if current_time - self.last_time >= 1.0:
            self.fps = self.frame_count
            self.frame_count = 0
            self.last_time = current_time
        
        fps_text = f"FPS: {self.fps}"
        cv2.putText(result_frame, fps_text, (10, 60), 
                    cv2.FONT_HERSHEY_SIMPLEX, 1, (255, 255, 255), 2)
        
        # Display overall status
        status_color = (0, 255, 0) if not alert else (0, 0, 255)
        cv2.putText(result_frame, overall_status, (10, 120), 
                    cv2.FONT_HERSHEY_SIMPLEX, 1, status_color, 2)
        
        # Display detection statistics
        stats_text = f"Top: {len(top_marks)}/4, Bottom: {len(bottom_marks)}/8"
        cv2.putText(result_frame, stats_text, (10, 180), 
                    cv2.FONT_HERSHEY_SIMPLEX, 1, (255, 255, 255), 2)
        
        # Display confidence threshold
        conf_text = f"Confidence: {self.confidence_threshold:.2f}"
        cv2.putText(result_frame, conf_text, (10, frame.shape[0] - 40), 
                    cv2.FONT_HERSHEY_SIMPLEX, 0.7, (0, 255, 255), 2)
        
        return result_frame, overall_status, alert

    """Display status of 4 tops - FORCE to show distance information"""
    def _display_top_status(self, frame):
        # Display status on right side of screen
        start_y = 240
        line_height = 40  # For first line
        
        for i in range(4):
            result = self.top_status[i]
            y_pos = start_y + i * (line_height + 45)  # Increased spacing
            
            # ALWAYS SHOW SOMETHING - even if not detected
            if not result['detected']:
                # Top not detected - show in gray
                line1 = f"Top{i+1}: Not detected"
                cv2.putText(frame, line1, (frame.shape[1] - 400, y_pos), 
                           cv2.FONT_HERSHEY_SIMPLEX, 0.8, (100, 100, 100), 2)
                
            elif result['matched']:
                # Top detected AND matched - show ALL distance info
                status = result['status']
                color = (0, 255, 0) if status == 'OK' else (0, 0, 255)  # Green/Red
                
                # Line 1: Main status
                line1 = f"Top{i+1}: {result['bottom_type']} ({status})"
                cv2.putText(frame, line1, (frame.shape[1] - 400, y_pos), 
                           cv2.FONT_HERSHEY_SIMPLEX, 0.8, color, 2)
                
                # Line 2: Y-difference and threshold
                line2 = f"  Y-diff: {result['y_diff']:.0f}/{result['threshold']}"
                cv2.putText(frame, line2, (frame.shape[1] - 400, y_pos + 25), 
                           cv2.FONT_HERSHEY_SIMPLEX, 0.8, color, 2)
                
                # Line 3: Front bottom distances
                line3 = f"  Front: X={result['x_diff_front']:.0f}, Y={result['y_diff_front']:.0f}"
                cv2.putText(frame, line3, (frame.shape[1] - 400, y_pos + 45), 
                           cv2.FONT_HERSHEY_SIMPLEX, 0.8, (200, 200, 200), 2)
                
                # Line 4: Back bottom distances
                line4 = f"  Back: X={result['x_diff_back']:.0f}, Y={result['y_diff_back']:.0f}"
                cv2.putText(frame, line4, (frame.shape[1] - 400, y_pos + 65), 
                           cv2.FONT_HERSHEY_SIMPLEX, 0.8, (200, 200, 200), 2)
                
            else:
                # Top detected but not matched - show why
                status_text = result['status']
                # Line 1: Status
                line1 = f"Top{i+1}: {status_text}"
                cv2.putText(frame, line1, (frame.shape[1] - 400, y_pos), 
                           cv2.FONT_HERSHEY_SIMPLEX, 0.8, (255, 165, 0), 2)
                
                # If we have any distance info, show it
                if result['x_diff_front'] > 0 or result['x_diff_back'] > 0:
                    line2 = f"  Front: X={result['x_diff_front']:.0f}, Y={result['y_diff_front']:.0f}"
                    cv2.putText(frame, line2, (frame.shape[1] - 400, y_pos + 25), 
                               cv2.FONT_HERSHEY_SIMPLEX, 0.8, (200, 200, 200), 2)
                    
                    line3 = f"  Back: X={result['x_diff_back']:.0f}, Y={result['y_diff_back']:.0f}"
                    cv2.putText(frame, line3, (frame.shape[1] - 400, y_pos + 45), 
                               cv2.FONT_HERSHEY_SIMPLEX, 0.8, (200, 200, 200), 2)

    def get_latest_result(self):
        """Get latest processing result"""
        with self.frame_lock:
            if self.latest_result is None:
                return None, "No result", False
            return self.latest_result

    def stop_processing(self):
        """Stop processing"""
        self.processing = False
        if self.processing_thread and self.processing_thread.is_alive():
            self.processing_thread.join(timeout=1.0)

        with self.frame_lock:
            self.latest_frame = None
            self.latest_result = None
            
        # Print detection statistics
        if self.detection_log:
            total_frames = len(self.detection_log)
            frames_with_detections = sum(1 for log in self.detection_log if log['num_boxes'] > 0)
            print(f"\nDetection statistics:")
            print(f"  Total frames processed: {total_frames}")
            print(f"  Frames with detections: {frames_with_detections}")
            print(f"  Detection rate: {frames_with_detections/total_frames:.1%}" if total_frames > 0 else "N/A")


def open_rtsp_camera():
    """
    Open RTSP camera connection
    Returns VideoCapture object
    """
    # RTSP URL configuration 
    rtsp_url = "rtsp://192.168.0.11:554/user=admin&password=&channel=1&stream=0.sdp?"
    
    print("Connecting to RTSP camera...")
    
    # Open camera
    cap = cv2.VideoCapture(rtsp_url)
    
    # Set parameters to optimize RTSP connection 
    cap.set(cv2.CAP_PROP_BUFFERSIZE, 1)           # Reduce buffer
    
    # Check if connection successful
    if not cap.isOpened():
        print(" RTSP camera connection failed! Trying local camera...")
        cap = cv2.VideoCapture(0)  # Try local camera
        if not cap.isOpened():
            print(" Local camera also failed!")
            return None
    
    # Test reading one frame
    ret, frame = cap.read()
    if not ret:
        print(" Cannot read frame from camera")
        cap.release()
        return None
    
    print(" RTSP camera connected successfully!")
    print(f"  Frame size: {frame.shape}")
    return cap


def get_scaled_size(original_width, original_height, target_width=1200):
    """
    Calculate scaled size proportionally
    original_width, original_height: Original frame dimensions
    target_width: Target width (default 1200)
    Returns: (scaled_width, scaled_height)
    """
    # Calculate scaling ratio
    scale = target_width / original_width
    scaled_width = target_width
    scaled_height = int(original_height * scale)
    
    return scaled_width, scaled_height


def main():
    print("Multi-plug airtightness detection system")
    print("=" * 60)
    print("Configuration: 4 tops + 8 bottoms")
    print("Each top corresponds to two bottoms in the same column")
    print("Determine front/back bottom insertion based on x-distance")
    print("Determine insertion success based on y-difference")
    print("=" * 60)
    
    # Initialize detector
    detector = MultiPlugDetector('best.pt')
    
    # Display threshold configuration
    print("\nThreshold configuration:")
    for col_idx, thresholds in detector.Y_DIFF_THRESHOLDS.items():
        print(f"  Column {col_idx+1}: front bottom={thresholds['front']}px, back bottom={thresholds['back']}px")
    
    # Open RTSP camera
    cap = open_rtsp_camera()
    if cap is None:
        print("Cannot open camera, exiting")
        return
    
    print("\nStarting detection... Press 'q' to exit")
    print("Press 's' to save current frame")
    print("Press '+' to increase confidence threshold")
    print("Press '-' to decrease confidence threshold")
    print("-" * 40)
    
    window_name = 'Multi-plug airtightness detection system'
    cv2.namedWindow(window_name, cv2.WINDOW_NORMAL)
    
    # Target display width
    target_display_width = 1200
    
    # Connection status tracking
    connection_retry_count = 0
    MAX_RETRY_COUNT = 3
    
    # Skip first few frames to stabilize camera
    print("Waiting for camera to stabilize...")
    for _ in range(10):
        ret, frame = cap.read()
        if not ret:
            print("Failed to read initial frames")
            break
    
    try:
        while True:
            # Read frame
            ret, frame = cap.read()
            if not ret:
                print("Failed to read frame, attempting to reconnect...")
                connection_retry_count += 1
                
                if connection_retry_count >= MAX_RETRY_COUNT:
                    print("Too many retries, exiting")
                    break
                
                # Attempt to reconnect
                cap.release()
                time.sleep(1)
                cap = open_rtsp_camera()
                
                connection_retry_count = 0
                continue
            
            # Reset retry count
            connection_retry_count = 0
            
            # Process frame asynchronously
            detector.process_frame_async(frame)
            
            # Get latest result
            result_frame, status, alert = detector.get_latest_result()
            
            display_frame = result_frame if result_frame is not None else frame
            
            # Get actual frame dimensions
            original_height, original_width = display_frame.shape[:2]
            
            # Calculate scaled size proportionally (maintain aspect ratio)
            scaled_width, scaled_height = get_scaled_size(
                original_width, original_height, target_display_width
            )
            
            # Scale image
            scaled_frame = cv2.resize(display_frame, (scaled_width, scaled_height))
            
            # Add information
            info_text = f"Original: {original_width}x{original_height} | Display: {scaled_width}x{scaled_height}"
            cv2.putText(scaled_frame, info_text, (10, scaled_height - 20),
                       cv2.FONT_HERSHEY_SIMPLEX, 0.7, (0, 255, 255), 2)
            
            # Display video
            cv2.imshow(window_name, scaled_frame)
            
            # Key handling
            key = cv2.waitKey(1) & 0xFF
            if key == ord('q'):
                print("User exited")
                break
            elif key == ord('s'):
                # Save current frame
                timestamp = time.strftime("%Y%m%d_%H%M%S")
                filename = f"snapshot_{timestamp}.jpg"
                if result_frame is not None:
                    cv2.imwrite(filename, result_frame)
                else:
                    cv2.imwrite(filename, frame)
                print(f"Screenshot saved: {filename}")
            elif key == ord('+'):
                detector.confidence_threshold = min(0.9, detector.confidence_threshold + 0.05)
                print(f"Confidence threshold increased to: {detector.confidence_threshold:.2f}")
            elif key == ord('-'):
                detector.confidence_threshold = max(0.1, detector.confidence_threshold - 0.05)
                print(f"Confidence threshold decreased to: {detector.confidence_threshold:.2f}")
    
    except KeyboardInterrupt:
        print("\nProgram interrupted")
    except Exception as e:
        print(f"Program error: {e}")
    finally:
        # Cleanup resources
        detector.stop_processing()
        if cap is not None:
            cap.release()
        cv2.destroyAllWindows()
        print("\nProgram ended")


if __name__ == "__main__":
    main()