package me.callmeavarice.leapmotion;

import com.leapmotion.leap.*;

import java.io.IOException;

public class App
{
    public static void main( String[] args )
    {
        // PinchGestureListener listener = new PinchGestureListener();
        RightHandFlipGestureListener listener = new RightHandFlipGestureListener();
        Controller controller = new Controller();

        // Have the sample listener receive events from the controller
        controller.addListener(listener);

        // Keep this process running until Enter is pressed
        System.out.println("Press Enter to quit...");
        try {
            System.in.read();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Remove the sample listener when done
        controller.removeListener(listener);
    }
}

class PinchGestureListener extends Listener {

    private boolean fingersPinched = false;
    private float pinchStartZPosition = 0f;
    private float pinchEndZPosition = 0f;

    private static final float FINGERS_PINCHED_X_AXIS_THRESHOLD = 20.5f;
    private static final float FINGERS_PINCH_RELEASED_X_AXIS_THRESHOLD = 5.5f;
    private static final int PREVIOUS_FRAME_ID = 1;

    @Override
    public void onFrame(Controller controller) {
        // Get the most recent frame
        Frame frame = controller.frame();

        // Check to make sure that we can see a hand
        if (!frame.hands().empty()) {
            HandList handsInView = frame.hands();

            // For simplicity let's work on the logic that if they are using the pinball machine's plunger that only one hand will be in view
            if(handsInView.count() == 1) {
                // Get the hand in view
                Hand hand = handsInView.get(0);

                // Check if the hand has any fingers extended
                FingerList fingers = hand.fingers();
                if (!fingers.empty()) {
                    int numFingers = fingers.count();
                    // When two fingers get too close they register as one, so we need to cover this edge case
                    if (fingersPinched && numFingers == 1) {
                        // Do nothing - for now
                    }
                    // For simplicity assume that the gesture will only be made with two fingers visible
                    else if (numFingers == 2) {
                        // Since the plunger is on the right hand side, of the pinball machine, we will assume that they will use their right hand
                        Finger thumb = fingers.leftmost();
                        Finger indexFinger = fingers.frontmost();

                        Vector thumbPosition = thumb.tipPosition();
                        Vector indexFingerPosition = indexFinger.tipPosition();

                        float pinchDistance = indexFingerPosition.getX()-thumbPosition.getX();
                        if(pinchDistance <= FINGERS_PINCHED_X_AXIS_THRESHOLD) {
                            if(!fingersPinched) {
                                fingersPinched = true;
                                System.out.println("FINGERS PINCHED");
                                pinchStartZPosition = indexFingerPosition.getZ();
                                // Stops the distance being negative if the frame after the pinch has been initialised does not contain a pinch gesture
                                // 0 - pinchStartZPosition used to be calculated in this situation.
                                pinchEndZPosition = pinchStartZPosition;
                            }
                            // Keep track of the pinch's position just in case we lose track of the hand before the pinch is registered as released
                            else {
                                int indexFingerId = indexFinger.id();
                                float currentZPosition = indexFingerPosition.getZ();
                                Frame previousFrame = controller.frame(PREVIOUS_FRAME_ID);
                                if(previousFrame!= null && previousFrame.isValid()) {
                                    Finger indexFingerLastFrame = previousFrame.finger(indexFingerId);
                                    if(indexFingerLastFrame != null && indexFingerLastFrame.isValid()) {
                                        float indexFingerLastFrameZPosition = indexFingerLastFrame.tipPosition().getZ();
                                        float indexFingerPositionDelta = currentZPosition - indexFingerLastFrameZPosition;

                                        // The pinch is being moved away from the screen/towards the user
                                        if(indexFingerPositionDelta > 0) {
                                            pinchEndZPosition = currentZPosition;
                                        }
                                        // The pinch is moving back towards the screen
                                        else {
                                            // The user is tweaking their pinch gesture slightly before releasing it
                                            if (currentZPosition > pinchStartZPosition) {
                                                pinchEndZPosition = currentZPosition;
                                            }
                                            // The user has gone closer to the screen than where their gesture originated (negative on the Z axis)
                                            else {
                                                pinchEndZPosition = pinchStartZPosition;
                                            }
                                        }
                                    }
                                }


                            }
                        } else if (fingersPinched && pinchDistance > FINGERS_PINCHED_X_AXIS_THRESHOLD + FINGERS_PINCH_RELEASED_X_AXIS_THRESHOLD){
                            System.out.println("FINGERS PINCH RELEASED");
                            fingersPinched = false;

                            pinchEndZPosition = indexFingerPosition.getZ();
                            if(pinchEndZPosition < pinchStartZPosition) {
                                System.out.println("The user has gone negative on the Z Axis, defaulting to the start position");
                                pinchEndZPosition = pinchStartZPosition;
                            }

                            System.out.println("Distance pinched = " + (pinchEndZPosition - pinchStartZPosition));
                        }


                        /*
                        System.out.println("Distance between them "+ indexFingerPosition.distanceTo(thumbPosition));
                        System.out.println("Distance between them (minus) "+ indexFingerPosition.minus(thumbPosition));
                        System.out.println("Distance between them (X axis) "+ (indexFingerPosition.getX()-thumbPosition.getX()));
                        */
                    }
                    // 3+ fingers are visible - for now interpret this as the end of the gesture
                    else {
                        if(fingersPinched) {
                            System.out.println("No longer pinching");
                            System.out.println("Distance pinched = " + (pinchEndZPosition - pinchStartZPosition));
                        }
                        fingersPinched = false;
                    }
                }
                // No fingers are visible - if we were gesturing then see how far back the pinch was drawn
                else {
                    if(fingersPinched) {
                        System.out.println("No longer pinching");
                        System.out.println("Distance pinched = " + (pinchEndZPosition - pinchStartZPosition));
                    }
                    fingersPinched = false;
                }
            }
        } else {
            if(fingersPinched) {
                System.out.println("No longer pinching");
                System.out.println("Distance pinched = " + (pinchEndZPosition - pinchStartZPosition));
            }
            fingersPinched = false;
        }
    }
}

class RightHandFlipGestureListener extends Listener {
    public static final int PREVIOUS_FRAME_ID = 1;

    private static final float RIGHT_FLIPPER_MAX_HEIGHT = -0.40f;
    private float RIGHT_FLIPPER_MIN_HEIGHT = 0.50f;

    private FlipperPosition flipperPosition = FlipperPosition.DOWN;

    private static final int NUMBER_OF_FLIPPER_CALIBRATION_FRAMES = 5;
    private static final int FLIPPER_VELOCITY_THRESHOLD = 75;



    @Override
    public void onFrame(Controller controller) {
        // Get the most recent frame
        Frame frame = controller.frame();

        // Check to make sure that we can see a hand
        if (!frame.hands().empty()) {
            HandList handsInView = frame.hands();

            // For simplicity let's work with only the right hand in view
            if(handsInView.count() == 1) {
                // Get the hand in view
                Hand hand = handsInView.get(0);

                // Check if the hand has any fingers extended
                FingerList fingers = hand.fingers();
                if (!fingers.empty()) {
                    int numFingers = fingers.count();

                    // Start flipper logic
                    // Assume that the user does not have their arms crossed
                    Hand rightHand = handsInView.rightmost();

                    Finger rightFlipperFinger = null;

                    if (!rightHand.fingers().isEmpty()) {
                        rightFlipperFinger= rightHand.fingers().leftmost();
                    }

                    if(rightFlipperFinger != null && rightFlipperFinger.isValid()) {
                        float currentZDirection = rightFlipperFinger.direction().getZ();

                        if(flipperPosition != FlipperPosition.UP && currentZDirection <= RIGHT_FLIPPER_MAX_HEIGHT) {
                            flipperPosition = FlipperPosition.UP;
                            System.out.println("UP");
                        } else if (flipperPosition == FlipperPosition.UP && currentZDirection > RIGHT_FLIPPER_MAX_HEIGHT) {
                            flipperPosition = FlipperPosition.FALLING;
                            System.out.println("FALLING");
                        } else if (flipperPosition != FlipperPosition.DOWN && currentZDirection >= RIGHT_FLIPPER_MIN_HEIGHT) {
                            System.out.println("DOWN");
                            flipperPosition = FlipperPosition.DOWN;
                        }
                    }
                }
            }
        }
    }

    private enum FlipperPosition {
        UP, DOWN, FALLING
    }
}
