import pandas as pd
import matplotlib.pyplot as plt
from scipy.integrate import cumtrapz
import glob


def grey_code_extraction_2bit(a, b):
    if (a is None or len(a) == 0 or b is None or len(b) == 0):
        ValueError(" grey_code_extraction:  invalid parameters ")
    i = 0
    bit_str = ''
    while(i + jump < len(a) or i + jump < len(b)):        
        if (a[i + jump] - a[i] >= 0):
            bit_str += '0'
            if (b[i + jump]- b[i] >= 0):
                bit_str += '0'
            else:
                bit_str += '1'
        else:
            bit_str += '1'
            if (b[i + jump] - b[i] >= 0):
                bit_str += '1'
            else:
                bit_str += '0'
        i += 1
    return bit_str

# jump in terms of datapoint used for extracting the gray code
jump = 2
threshold = 0.7
window_range = 0.2
zeroes = [0.0, 0.0]
calib_acc = {'min': -0.2, 'max': 0.2}
calib_vel = 0.02
results = {'matching_success': [], 
            'non_matching_success': [], 
            'false_positives': [], 
            'false_negatives': []}


files_phone = glob.glob('Test_Data/sec_protocol_tests/floating/Drawing_Data/*_smartphone_sample.csv')
files_watch = glob.glob('Test_Data/sec_protocol_tests/floating/Accelerometer_Data/*_watch_sample.csv')

files_phone.sort()
files_watch.sort()
success = 0
false_positives = 0
false_negatives = 0


for file_phone in files_phone:
    # DataFrame collection from files
    data_phone = pd.read_csv(file_phone, engine='python')
    for file_watch in files_watch:
        data_watch = pd.read_csv(file_watch, engine='python')

        file_phone_identifier = file_phone.split('/')[-1].split('_')[1]
        file_watch_identifier = file_watch.split('/')[-1].split('_')[1]

        x_acc_filtered = data_watch['x_lin_acc'].to_list()
        y_acc_filtered = data_watch['y_lin_acc'].to_list()

        x_vel_filtered = data_phone['x_velocity_filtered'].to_list()
        y_vel_filtered = (data_phone['y_velocity_filtered'] * -1).to_list()

        #Acceleration Noise filtering
        for i in range(0, len(x_acc_filtered)):
            if x_acc_filtered[i] <= calib_acc['max'] and x_acc_filtered[i] >= calib_acc['min']:
                x_acc_filtered[i] = 0
            if y_acc_filtered[i] <= calib_acc['max'] and y_acc_filtered[i] >= calib_acc['min']:
                y_acc_filtered[i] = 0

        #Velocity Noise filtering
        for i in range(0, len(x_vel_filtered)):
            if x_vel_filtered[i] <= calib_vel and x_vel_filtered[i] >= -calib_vel:
                x_vel_filtered[i] = 0
            if y_vel_filtered[i] <= calib_vel and y_vel_filtered[i] >= -calib_vel:
                y_vel_filtered[i] = 0

        x_acc_non_zero = [idx for idx, val in enumerate(x_acc_filtered) if val != 0]
        y_acc_non_zero = [idx for idx, val in enumerate(y_acc_filtered) if val != 0]

        if not x_acc_non_zero and not y_acc_non_zero:
            print(file_phone_identifier, file_watch_identifier, 'No motion detected from accelerometer!')
            continue
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
            print(file_phone_identifier, file_watch_identifier, 'No motion detected from smartphone!')
            continue
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

        watch_vel_greycode = grey_code_extraction_2bit(x_vel, y_vel)
        phone_vel_greycode = grey_code_extraction_2bit(x_vel_filtered, y_vel_filtered)

        match_result = 0.0
        walker = 0
        watch_samples_more = len(watch_vel_greycode) > len(phone_vel_greycode)
        n = len(phone_vel_greycode) if watch_samples_more else len(watch_vel_greycode)
        window = abs(len(phone_vel_greycode) - len(watch_vel_greycode))

        if window > n * window_range:
            if file_phone_identifier != file_watch_identifier:
                success += 1
            else:
                print(file_phone_identifier, file_watch_identifier, 'Number of samples mismatch, aborting.')
                false_negatives += 1
            continue

        while walker <= window:
            matching_codes_count = 0
            for i in range(0, n):
                if watch_samples_more:
                    if watch_vel_greycode[i + walker] == phone_vel_greycode[i]:
                        matching_codes_count += 1

                elif watch_vel_greycode[i] == phone_vel_greycode[i + walker]:
                    matching_codes_count += 1
            
            curr_match_result = matching_codes_count / n
            
            if curr_match_result > match_result:
                match_result = curr_match_result
            
            if curr_match_result >= threshold:
                break
            
            walker += 1

        if file_phone_identifier == file_watch_identifier:
            if match_result >= threshold:
                success += 1
                results['matching_success'].append(match_result)
            else:
                print(file_phone_identifier, file_watch_identifier, match_result)
                false_negatives += 1
                results['false_negatives'].append(match_result)
        else:
            if match_result < threshold:
                results['non_matching_success'].append(match_result)
                success += 1
            else:
                print(file_phone_identifier, file_watch_identifier, match_result)
                results['false_positives'].append(match_result)
                false_positives += 1

        
print('-------------------------------')
print('Result using 2-bit encoding:')
print('Total tests:', success + false_positives + false_negatives)
print('Success:', success)
print('False positives:', false_positives)
print('False negatives:', false_negatives)
print('-------------------------------')
print('Matching success:', min(results['matching_success']), max(results['matching_success']))
print('Non-matching success:', min(results['non_matching_success']), max(results['non_matching_success']))

if results['false_positives']:
    print('False positives:', min(results['false_positives']), max(results['false_positives']))
if results['false_negatives']:
    print('False negatives:', min(results['false_negatives']), max(results['false_negatives']))
print('-------------------------------')


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