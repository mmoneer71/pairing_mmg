import pandas as pd
import matplotlib.pyplot as plt
import numpy as np
from scipy.fftpack import fftfreq, fft, ifft
import scipy.signal as sig
import smoothed_zscore as sz
from scipy.integrate import romb
from scipy.integrate import cumtrapz


def calculate_derivative_list(timestampList, toCalcList):
    if (timestampList is None or len(timestampList) == 0 or toCalcList is None 
        or len(toCalcList) == 0):
        ValueError(" calculate_derivative_list:  invalid parameters ")
    toCalc = np.asarray(toCalcList)
    resultList = []    
    resultList.append(0)
    for i in range(len(toCalc)):
        if i == len(toCalc)-1: 
            break
        resultList.append((toCalc[i + 1] - toCalc[i]) / (timestampList[i + 1] - timestampList[i]))
    return resultList

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
vel_file_path = 'Test_Data/fusion_tests/type_acc_lin1/Drawing_Data/2020-03-24_1_smartphone_sample.csv'
acc_file_path = 'Test_Data/fusion_tests/type_acc_lin1/Accelerometer_Data/2020-03-24_1_watch_sample.csv'


# DataFrame collection from files
data_phone = pd.read_csv(vel_file_path, engine='python')
data_watch = pd.read_csv(acc_file_path, engine='python')
#-4.8085445E-4 -1.5577588E-4
calib_acc = {'x_avg': -0.07737972, 'y_avg':-0.032692935, 'x_min':-0.2, 'y_min':-0.2, 'x_max': 0.2, 'y_max':0.2}
calib_vel = {'x_min':-0.03, 'y_min':-0.03, 'x_max': 0.03, 'y_max':0.03}


#data_watch['x_acc'] -= data_watch['x_acc'].mean()
#data_watch['y_acc'] -= data_watch['y_acc'].mean()


x_vel_filtered = data_phone['x_velocity_filtered'].to_list() #sig.savgol_filter(data_phone['x_velocity'], window_length_savgol, polyorder_savgol)
y_vel_filtered = (data_phone['y_velocity_filtered'] * -1).to_list() #sig.savgol_filter(data_phone['y_velocity'], window_length_savgol, polyorder_savgol)

for i in range(0, len(data_watch['x_lin_acc'])):
     if data_watch['x_lin_acc'][i] <= calib_acc['x_max'] and data_watch['x_lin_acc'][i] >= calib_acc['x_min']:
         #print(data_watch['x_acc'][i])
         data_watch['x_lin_acc'][i] = 0
     if data_watch['y_lin_acc'][i] <= calib_acc['y_max'] and data_watch['y_lin_acc'][i] >= calib_acc['y_min']:
         #print(data_watch['y_acc'][i])
         data_watch['y_lin_acc'][i] = 0


for i in range(0, len(x_vel_filtered)):
     if x_vel_filtered[i] <= calib_vel['x_max'] and x_vel_filtered[i] >= calib_vel['x_min']:
         #print(data_watch['x_acc'][i])
         x_vel_filtered[i] = 0
     if y_vel_filtered[i] <= calib_vel['y_max'] and y_vel_filtered[i] >= calib_vel['y_min']:
         #print(data_wax_veltch['y_acc'][i])
         y_vel_filtered[i] = 0


#plt.plot(data_watch['timestamp'], output)
#plt.figure()
#plt.plot(data_watch['timestamp'], data_watch['x_lin_acc'], label='lin_acc')
#plt.plot(data_watch['timestamp'], data_watch['x_acc'], label='acc')
#plt.plot(range(0, len(data_watch['x_lin_acc'])), data_watch['x_lin_acc'])
#plt.plot(range(0, len(x_vel_refined)), x_vel_refined)


for i in range(0, len(data_watch['x_lin_acc'])):
    if data_watch['x_lin_acc'][i] != 0:
        x_acc_start = i #print(i, len(data_watch['x_lin_acc']))
        break

for i in range(len(data_watch['x_lin_acc']) -1 , -1, -1):
    if data_watch['x_lin_acc'][i] != 0:
        x_acc_end = i #print(i, len(data_watch['x_lin_acc']))
        break

for i in range(0, len(data_watch['y_lin_acc'])):
    if data_watch['y_lin_acc'][i] != 0:
        y_acc_start = i #print(i, len(data_watch['x_lin_acc']))
        break

for i in range(len(data_watch['y_lin_acc']) -1 , -1, -1):
    if data_watch['y_lin_acc'][i] != 0:
        y_acc_end = i #print(i, len(data_watch['x_lin_acc']))
        break

if x_acc_start < y_acc_start:
    acc_start = x_acc_start
else:
    acc_start = y_acc_start

if x_acc_end > y_acc_end:
    acc_end = x_acc_end
else:
    acc_end = y_acc_end


#plt.plot(range(0, len(data_watch['x_lin_acc'])), data_watch['x_lin_acc'])
#plt.figure()
#plt.plot(range(0, len(data_watch['y_lin_acc'])), data_watch['y_lin_acc'])
#plt.figure()
x_acc_filtered = data_watch['x_lin_acc'][acc_start - 2 : acc_end + 2].to_list()
y_acc_filtered = data_watch['y_lin_acc'][acc_start - 2 : acc_end + 2].to_list()


x_vel = cumtrapz(x_acc_filtered)
x_pos = cumtrapz(x_vel)

#plt.plot(range(0, len(data_phone['x_velocity'])), data_phone['x_velocity'])



y_vel = cumtrapz(y_acc_filtered)
y_pos = cumtrapz(y_vel)

if len(y_vel_filtered) > len(y_vel) or len(x_vel_filtered) > len(x_vel):
    print("Failure: Length error")
    exit(0)

#TODO: revisit
y_vel = y_vel[:len(y_vel_filtered)]
x_vel = x_vel[:len(x_vel_filtered)]

watch_vel_greycode_2bit = grey_code_extraction_2bit(x_vel, y_vel)
phone_vel_greycode_2bit = grey_code_extraction_2bit(x_vel_filtered, y_vel_filtered)

vel_match = 0
for i in range(0, len(watch_vel_greycode_2bit)):
    #print(watch_acc_greycode_2bit[i], phone_acc_greycode_2bit[i])
    if watch_vel_greycode_2bit[i] == phone_vel_greycode_2bit[i]:
        vel_match += 1

vel_match = vel_match / len(watch_vel_greycode_2bit)

if vel_match >= threshold:
    print("Velocity - Success: " + str(vel_match))
else:
    print("Velocity - Failure: " + str(vel_match))


x_acc = calculate_derivative_list(data_phone['timestamp'], x_vel_filtered)
y_acc = calculate_derivative_list(data_phone['timestamp'], y_vel_filtered)


watch_acc_greycode_2bit = grey_code_extraction_2bit(x_acc_filtered, y_acc_filtered)
phone_acc_greycode_2bit = grey_code_extraction_2bit(x_acc, y_acc)

acc_match = 0
for i in range(0, len(phone_acc_greycode_2bit)):
    #print(watch_acc_greycode_2bit[i], phone_acc_greycode_2bit[i])
    if watch_acc_greycode_2bit[i] == phone_acc_greycode_2bit[i]:
        acc_match += 1

acc_match = acc_match / len(phone_acc_greycode_2bit)

if acc_match >= threshold:
    print("Acc - Success: " + str(acc_match))
else:
    print("Acc - Failure: " + str(acc_match))


plt.plot(range(0, len(x_acc_filtered)), x_acc_filtered)
plt.figure()
plt.plot(range(0, len(x_vel_filtered)), x_vel_filtered)
plt.show()