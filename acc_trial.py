import pandas as pd
import matplotlib.pyplot as plt
import numpy as np
from scipy.fftpack import fftfreq, fft, ifft
import scipy.signal as sig
from scipy.integrate import cumtrapz


def grey_code_extraction_2bit(a, b):
    if (a is None or len(a) == 0 or b is None or len(b) == 0):
        ValueError(" grey_code_extraction:  invalid parameters ")
    i = 0
    bits_str = np.array([], dtype = str)
    while(i + jump < len(a) or i + jump < len(b)):        
        if (a[i + jump] - a[i] >= 0):
            app_str = '0'
            if (b[i + jump]- b[i] >= 0):
                app_str += '0'
            else:
                app_str += '1'
        else:
            app_str = '1'
            if (b[i + jump] - b[i] >= 0):
                app_str += '1'
            else:
                app_str += '0'
        bits_str = np.append(bits_str, app_str)
        i += 1
    return bits_str

# jump in terms of datapoint used for extracting the grey codedata_watch['x_acc_fin'][firstpeak_index:len(x_dy)]
jump = 2
threshold = 0.5
epsilon = 0.2
window_range = 0.25
zeroes = [0.0, 0.0]
vel_file_path = 'Test_Data/sec_protocol_tests/Drawing_Data/2020-05-08_6_smartphone_sample.csv'
acc_file_path = 'Test_Data/sec_protocol_tests/Accelerometer_Data/2020-05-08_6_watch_sample.csv'


# DataFrame collection from files
data_phone = pd.read_csv(vel_file_path, engine='python')
data_watch = pd.read_csv(acc_file_path, engine='python')

x_acc_filtered = data_watch['x_lin_acc'].to_list()
y_acc_filtered = data_watch['y_lin_acc'].to_list()
acc_magnitude = np.sqrt(np.power(x_acc_filtered, 2) + np.power(y_acc_filtered, 2))

x_vel_filtered = data_phone['x_velocity_filtered'].to_list()
y_vel_filtered = (data_phone['y_velocity_filtered'] * -1).to_list()

noise_factor = 4
calib_acc = {'min': -0.4, 'max': 0.4}

calib_vel = 0.03

#Acceleration Noise filtering
for i in range(0, len(x_acc_filtered)):
     if x_acc_filtered[i] <= calib_acc['max'] and x_acc_filtered[i] >= calib_acc['min']:
         #print(data_watch['x_acc'][i])
         x_acc_filtered[i] = 0
     if y_acc_filtered[i] <= calib_acc['max'] and y_acc_filtered[i] >= calib_acc['min']:
         #print(data_watch['y_acc'][i])
         y_acc_filtered[i] = 0

#Velocity Noise filtering
for i in range(0, len(x_vel_filtered)):
     if x_vel_filtered[i] <= calib_vel and x_vel_filtered[i] >= -calib_vel:
         #print(data_watch['x_acc'][i])
         x_vel_filtered[i] = 0
     if y_vel_filtered[i] <= calib_vel and y_vel_filtered[i] >= -calib_vel:
         #print(data_wax_veltch['y_acc'][i])
         y_vel_filtered[i] = 0

x_acc_non_zero = [idx for idx, val in enumerate(x_acc_filtered) if val != 0]
y_acc_non_zero = [idx for idx, val in enumerate(y_acc_filtered) if val != 0]

if not x_acc_non_zero and not y_acc_non_zero:
    print('No motion detected from accelerometer!')
    exit(0)
elif not x_acc_non_zero:
    acc_start = y_acc_non_zero[0]
    acc_end = y_acc_non_zero[-1]
elif not y_acc_non_zero:
    acc_start = x_acc_non_zero[0]
    acc_end = x_acc_non_zero[-1]
else:
    acc_start = x_acc_non_zero[0] if x_acc_non_zero[0] < y_acc_non_zero[0] else y_acc_non_zero[0]
    acc_end = x_acc_non_zero[-1] if x_acc_non_zero[-1] > y_acc_non_zero[-1] else y_acc_non_zero[-1]


x_vel_non_zero = [idx for idx, val in enumerate(x_vel_filtered) if val != 0]
y_vel_non_zero = [idx for idx, val in enumerate(y_vel_filtered) if val != 0]

if not x_vel_non_zero and not y_vel_non_zero:
    print('No motion detected from smartphone!')
    exit(0)
elif not x_vel_non_zero:
    vel_start = y_vel_non_zero[0]
elif not y_vel_non_zero:
    vel_start = x_vel_non_zero[0]
else:
    vel_start = x_vel_non_zero[0] if x_vel_non_zero[0] < y_vel_non_zero[0] else y_vel_non_zero[0]

x_acc_filtered = zeroes + x_acc_filtered[acc_start:acc_end] + zeroes
y_acc_filtered = zeroes + y_acc_filtered[acc_start:acc_end] + zeroes

x_vel_filtered = zeroes + x_vel_filtered[vel_start:] + zeroes
y_vel_filtered = zeroes + y_vel_filtered[vel_start:] + zeroes


x_vel = cumtrapz(x_acc_filtered)
x_vel = [0.0] + x_vel

y_vel = cumtrapz(y_acc_filtered)
y_vel = [0.0] + y_vel

print(len(x_acc_filtered), len(x_vel_filtered))


watch_vel_greycode_2bit = grey_code_extraction_2bit(x_vel, y_vel)
phone_vel_greycode_2bit = grey_code_extraction_2bit(x_vel_filtered, y_vel_filtered)

match_result = 0.0
walker = 0
watch_samples_more = len(watch_vel_greycode_2bit) > len(phone_vel_greycode_2bit)
n = len(phone_vel_greycode_2bit) if watch_samples_more else len(watch_vel_greycode_2bit)
window = abs(len(phone_vel_greycode_2bit) - len(watch_vel_greycode_2bit))

if window > n * window_range:
    print('Number of samples mismatch, aborting.')
    exit(0)

while walker <= window:
    matching_codes_count = 0
    for i in range(0, n):
        if watch_samples_more:
            if watch_vel_greycode_2bit[i + walker] == phone_vel_greycode_2bit[i]:
                matching_codes_count += 1

        elif watch_vel_greycode_2bit[i] == phone_vel_greycode_2bit[i + walker]:
            matching_codes_count += 1
    
    curr_match_result = matching_codes_count / n
    
    print(curr_match_result)
    if curr_match_result >= threshold:
        match_result = curr_match_result
        break

    if curr_match_result < epsilon:
        match_result = 0.0
        break
    
    if curr_match_result > match_result:
        match_result = curr_match_result
    
    walker += 1


if match_result >= threshold:
    print("Velocity - Success: " + str(match_result))
else:
    print("Velocity - Failure: " + str(match_result))


###################################Acceleration######################################
#x_acc = np.diff(x_vel_filtered)
#y_acc = np.diff(y_vel_filtered)


#x_acc = sig.resample(x_acc, len(x_acc_filtered))
#y_acc = sig.resample(y_acc, len(y_acc_filtered))



#watch_acc_greycode_2bit = grey_code_extraction_2bit(x_acc_filtered, y_acc_filtered)
#phone_acc_greycode_2bit = grey_code_extraction_2bit(x_acc, y_acc)

#acc_match = 0
#for i in range(0, len(phone_acc_greycode_2bit)):
    #print(watch_acc_greycode_2bit[i], phone_acc_greycode_2bit[i])
#    if watch_acc_greycode_2bit[i] == phone_acc_greycode_2bit[i]:
#        acc_match += 1

#acc_match = acc_match / len(phone_acc_greycode_2bit)

#if acc_match >= threshold:
#    print("Acc - Success: " + str(acc_match))
#else:
#    print("Acc - Failure: " + str(acc_match))