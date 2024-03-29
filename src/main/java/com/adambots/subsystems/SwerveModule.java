// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package com.adambots.subsystems;

import com.adambots.Constants.DriveConstants.ModulePosition;
import com.adambots.utils.Dash;
import com.adambots.Constants.ModuleConstants;
import com.ctre.phoenix6.hardware.CANcoder;
import com.revrobotics.CANSparkBase.IdleMode;
import com.revrobotics.CANSparkLowLevel.MotorType;
import com.revrobotics.CANSparkMax;
import com.revrobotics.RelativeEncoder;

import edu.wpi.first.math.controller.PIDController;
import edu.wpi.first.math.controller.ProfiledPIDController;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.kinematics.SwerveModulePosition;
import edu.wpi.first.math.kinematics.SwerveModuleState;
import edu.wpi.first.math.trajectory.TrapezoidProfile;
import edu.wpi.first.math.util.Units;

@SuppressWarnings("unused")
public class SwerveModule {
  private final CANSparkMax m_driveMotor;
  private final CANSparkMax m_turningMotor;

  private final CANcoder m_absoluteEncoder;
  private final RelativeEncoder m_driveEncoder;
  // private final CANCoderConfiguration m_canCoderConfig = new CANCoderConfiguration();

  private final PIDController m_drivePIDController =
      new PIDController(ModuleConstants.kPModuleDriveController, 0, 0);

  // Using a TrapezoidProfile PIDController to allow for smooth turning
  private final ProfiledPIDController m_turningPIDController =
      new ProfiledPIDController(
          ModuleConstants.kPModuleTurningController,
          ModuleConstants.kIModuleTurningController,
          ModuleConstants.kDModuleTurningController,
          new TrapezoidProfile.Constraints(
              ModuleConstants.kMaxModuleAngularSpeedRadiansPerSecond,
              ModuleConstants.kMaxModuleAngularAccelerationRadiansPerSecondSquared));

  private ModulePosition m_position;

  public void setPIDValues(double kP, double kI, double kD) {
    m_turningPIDController.setP(kP);
    m_turningPIDController.setI(kI);
    m_turningPIDController.setD(kD);
  }

  /**
   * Constructs a SwerveModule.
   *
   * @param position The position of this module (front or back, right or left)
   * @param driveMotorChannel The channel of the drive motor.
   * @param turningMotorChannel The channel of the turning motor.
   * @param driveEncoderChannels The channels of the drive encoder.
   * @param turningEncoderChannels The channels of the turning encoder.
   * @param driveEncoderReversed Whether the drive encoder is reversed.
   * @param turningEncoderReversed Whether the turning encoder is reversed.
   */
  public SwerveModule(
      ModulePosition position,
      int driveMotorChannel,
      int turningMotorChannel,
      int turningEncoderChannel,
      boolean driveEncoderReversed,
      boolean turningEncoderReversed) {
    
    this.m_position = position; // Use position.name() to get the name of the position as a String

    m_driveMotor = new CANSparkMax(driveMotorChannel, MotorType.kBrushless);
    m_driveMotor.setIdleMode(IdleMode.kBrake);
    m_driveMotor.setSmartCurrentLimit(32);
    m_driveMotor.enableVoltageCompensation(12.6);

    m_turningMotor = new CANSparkMax(turningMotorChannel, MotorType.kBrushless);
    m_turningMotor.setIdleMode(IdleMode.kBrake);
    m_turningMotor.setSmartCurrentLimit(21);
    m_turningMotor.enableVoltageCompensation(12.6);
    m_turningMotor.setInverted(true); //Added because PID values were negative NEEDS TESTING

    m_absoluteEncoder = new CANcoder(turningEncoderChannel);
    m_driveEncoder = m_driveMotor.getEncoder();

    //TODO: Utilize driveEncoder and turningEncoder Reversed flags - instead of negating Joystick values in RobotContainer
    // m_driveMotor.setInverted(driveEncoderReversed);
    
    // m_absoluteEncoder.clearStickyFaults();
    // m_driveMotor.clearFaults();
    // m_turningMotor.clearFaults();
    Dash.add("Cancoder: " + m_position.name(), () -> m_absoluteEncoder.getAbsolutePosition().getValueAsDouble()*360+180);

    m_turningPIDController.enableContinuousInput(-Math.PI, Math.PI);
    resetEncoders();
  }

  /**
   * Returns the current state of the module.
   *
   * @return The current state of the module.
   */
  public SwerveModuleState getState() {
    double speedMetersPerSecond = ModuleConstants.kDriveEncoderDistancePerPulse * m_driveEncoder.getVelocity();
    double turningRadians = Units.degreesToRadians(m_absoluteEncoder.getAbsolutePosition().getValueAsDouble()*360+180);
    return new SwerveModuleState(speedMetersPerSecond, new Rotation2d(turningRadians));
  }

  /**
   * Sets the desired state for the module.
   * @param desiredState Desired state with speed and angle.
   */
  public void setDesiredState(SwerveModuleState desiredState) {
    double speedMetersPerSecond = ModuleConstants.kDriveEncoderDistancePerPulse * m_driveEncoder.getVelocity(); //TODO: Fix this complete lack of sensible units
    double turningRadians = Units.degreesToRadians(m_absoluteEncoder.getAbsolutePosition().getValueAsDouble()*360+180);


    // desiredState.speedMetersPerSecond *= desiredState.angle.minus(new Rotation2d(turningRadians)).getCos(); //TODO: Test this

    // Optimize the reference state to avoid spinning further than 90 degrees
    SwerveModuleState state =
        SwerveModuleState.optimize(desiredState, new Rotation2d(turningRadians));

    // Calculate the drive output from the drive PID controller.
    final double driveOutput =
        m_drivePIDController.calculate(speedMetersPerSecond, state.speedMetersPerSecond);

    // Calculate the turning motor output from the turning PID controller.
    final double turnOutput =
        m_turningPIDController.calculate(turningRadians, state.angle.getRadians());

    // Calculate the turning motor output from the turning PID controller.

    m_driveMotor.set(driveOutput);
    m_turningMotor.set(turnOutput);
  }

  /**
   * Returns the current position of the module.
   *
   * @return The current position of the module.
   */
  public SwerveModulePosition getPosition() {
    double distance = m_driveEncoder.getPosition() * ModuleConstants.kDriveEncoderScale;
    double turningDistance = Units.degreesToRadians(m_absoluteEncoder.getAbsolutePosition().getValueAsDouble()*360+180);

    return new SwerveModulePosition(distance, new Rotation2d(turningDistance));
  }

  public void stop(){
    m_driveMotor.set(0);
    m_turningMotor.set(0);
  }

  /** Zeroes all the SwerveModule encoders. */
  public void resetEncoders() {
    m_driveEncoder.setPosition(0);
  }
}